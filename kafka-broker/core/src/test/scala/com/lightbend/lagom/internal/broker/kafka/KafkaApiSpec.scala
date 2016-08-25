/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.function.{ Function => JFunction }

import scala.collection.concurrent.TrieMap
import scala.compat.java8.FunctionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.concurrent.ScalaFutures

import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.kafka.KafkaLocalServer
import com.lightbend.lagom.javadsl.api.Descriptor
import com.lightbend.lagom.javadsl.api.ScalaService._
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.api.broker.{ Topic => ApiTopic }
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.broker.kafka.TopicProducer
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.japi.{ Pair => JPair }
import akka.stream.Materializer
import akka.stream.javadsl.{ Source => JSource }
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.testkit.EventFilter
import javax.inject.Inject
import play.api.ApplicationLoader
import play.api.Environment
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.guice.GuiceApplicationLoader

class KafkaApiSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  private val application = {
    val context = ApplicationLoader.createContext(Environment.simple())
    val builder = new GuiceApplicationBuilder(modules = Seq(KafkaApiSpec.testModule, KafkaApiSpec.noServiceLocatorModule, KafkaApiSpec.offsetTrackerModule, KafkaApiSpec.otherDependenciesModule))
    new GuiceApplicationLoader(builder).load(context)
  }

  @volatile private var kafkaServer = KafkaLocalServer(cleanOnStart = true)

  override def beforeAll() = {
    val system = application.injector.instanceOf(classOf[ActorSystem])
    Cluster(system).join(Cluster(system).selfAddress)
    kafkaServer.start()
  }

  override def afterAll() = {
    kafkaServer.stop()
    application.stop()
  }

  implicit val patience = PatienceConfig(30.seconds, 150.millis)

  "The Kafka message broker api" should {

    val testService = application.injector.instanceOf(classOf[KafkaApiSpec.TestService])

    "eagerly publish event stream registered in the service topic implementation" in {
      val publisher = KafkaApiSpec.test1Publisher
      val testTopic = testService.test1Topic()

      val messageReceived = Promise[String]()
      testTopic.subscribe().atLeastOnce(Flow.fromFunction { (message: String) =>
        messageReceived.trySuccess(message)
        Done
      }.asJava)

      val messageToPublish = "msg"
      publisher(0).success(new JPair(messageToPublish, Offset.NONE))

      messageToPublish shouldBe messageReceived.future.futureValue
    }

    "self-heal if failure occurs while running the publishing stream" in {
      implicit val system = application.injector.instanceOf(classOf[ActorSystem])
      val publisher = KafkaApiSpec.test2Publisher
      val testTopic = testService.test2Topic()

      val firstTimeReceived = Promise[String]()
      val secondTimeReceived = Promise[String]()
      val messageCommitFailureInjected = new CountDownLatch(1)

      testTopic.subscribe().atLeastOnce(Flow.fromFunction { (message: String) =>
        if (!firstTimeReceived.isCompleted) {
          firstTimeReceived.trySuccess(message)
          messageCommitFailureInjected.await()
        } else if (!secondTimeReceived.isCompleted)
          secondTimeReceived.trySuccess(message)
        else ()
        Done
      }.asJava)

      val firstMessageToPublish = "firstMessage"
      publisher(0).success(new JPair(firstMessageToPublish, Offset.NONE))

      // wait until first message is received
      firstMessageToPublish shouldBe firstTimeReceived.future.futureValue

      // now simulate a failure: this will results in an exception being thrown when committing
      // the offset of the first processed message.
      EventFilter[KafkaApiSpec.InMemoryOffsetTracker.FakeCassandraException]() intercept {
        KafkaApiSpec.InMemoryOffsetTracker.injectFailure = true
        // Let the flow processing continue. A failure is expected to occur when publishing the next message.
        messageCommitFailureInjected.countDown()
      }

      // After the exception was logged, let's remove the cause of the failure and check the stream computation resumes.
      KafkaApiSpec.InMemoryOffsetTracker.injectFailure = false

      // publish a second message.
      val secondMessageToPublish = "secondMessage"
      publisher(1).success(new JPair(secondMessageToPublish, Offset.NONE))

      // The subscriber flow should have self-healed and hence it will process the second message (this time, succeeding)
      secondMessageToPublish shouldBe secondTimeReceived.future.futureValue
    }

    "keep track of the read-side offset when publishing events" in {
      implicit val ec = application.injector.instanceOf(classOf[ExecutionContext])
      val info = application.injector.instanceOf(classOf[ServiceInfo])
      val publisher = KafkaApiSpec.test3Publisher
      val testTopic = testService.test3Topic()
      val offsetTracker = KafkaApiSpec.InMemoryOffsetTracker.of(testTopic.topicId(), info)

      // No message was consumed from this topic, hence we expect the last stored offset to be NONE 
      Offset.NONE shouldBe offsetTracker.map(_.lastOffset).futureValue

      val messageReceived = Promise[String]()
      testTopic.subscribe().atLeastOnce(Flow.fromFunction { (message: String) =>
        messageReceived.trySuccess(message)
        Done
      }.asJava)

      val messageToPublish = "msg"
      val messageOffset = Offset.sequence(1)
      publisher(0).success(new JPair(messageToPublish, messageOffset))
      messageToPublish shouldBe messageReceived.future.futureValue

      // After consuming a message we expect the offset store to have been updated with the offset of 
      // the consumed message
      messageOffset shouldBe offsetTracker.map(_.lastOffset).futureValue
    }

    "self-heal at-least-once consumer stream if a failure occurs" in {
      val publisher = KafkaApiSpec.test4Publisher
      val testTopic = testService.test4Topic()

      @volatile var failOnMessageReceived = true
      val messageReceived = Promise[String]()
      testTopic.subscribe().atLeastOnce(Flow.fromFunction { (message: String) =>
        if (failOnMessageReceived) {
          failOnMessageReceived = false
          throw new IllegalStateException("Simulate consumer failure")
        } else
          messageReceived.trySuccess(message)
        Done
      }.asJava)

      val messageSent = "msg"
      publisher(0).success(new JPair(messageSent, Offset.NONE))

      messageSent shouldBe messageReceived.future.futureValue
    }

    "self-heal at-most-once consumer stream if a failure occurs" in {
      implicit val mat = application.injector.instanceOf(classOf[Materializer])
      implicit val ec = application.injector.instanceOf(classOf[ExecutionContext])
      case object SimulateFailure extends RuntimeException

      val publisher = KafkaApiSpec.test5Publisher
      val testTopic = testService.test5Topic()

      // Let's publish a messages to the topic
      val message = "message"
      publisher(0).success(new JPair(message, Offset.NONE))

      // Now we register a consumer that will fail while processing a message. Because we are using at-most-once
      // delivery, the message that caused the failure won't be re-processed.
      @volatile var countProcessedMessages = 0
      val expectFailure = testTopic.subscribe().atMostOnceSource().asScala
        .via(Flow.fromFunction { (message: String) =>
          countProcessedMessages += 1
          throw SimulateFailure
        })
        .runWith(Sink.ignore)

      expectFailure.failed.futureValue shouldBe an[SimulateFailure.type]
      countProcessedMessages shouldBe 1
    }
  }

}

object KafkaApiSpec {

  private val test1Publisher = publisherFor(1)
  private val test2Publisher = publisherFor(2)
  private val test3Publisher = publisherFor(1)
  private val test4Publisher = publisherFor(1)
  private val test5Publisher = publisherFor(1)

  private def publisherFor(numberOfMessages: Int) = (0 until numberOfMessages).map(_ => Promise[JPair[String, Offset]]).toList

  trait TestService extends Service {
    def test1Topic(): ApiTopic[String]
    def test2Topic(): ApiTopic[String]
    def test3Topic(): ApiTopic[String]
    def test4Topic(): ApiTopic[String]
    def test5Topic(): ApiTopic[String]

    override def descriptor(): Descriptor =
      named("testservice")
        .publishing(
          topic("test1", test1Topic _),
          topic("test2", test2Topic _),
          topic("test3", test3Topic _),
          topic("test4", test4Topic _),
          topic("test5", test5Topic _)
        )
  }

  class TestServiceImpl @Inject() (topicProducer: TopicProducer) extends TestService {
    override def test1Topic(): ApiTopic[String] = createTopicProducer(test1Publisher)
    override def test2Topic(): ApiTopic[String] = createTopicProducer(test2Publisher)
    override def test3Topic(): ApiTopic[String] = createTopicProducer(test3Publisher)
    override def test4Topic(): ApiTopic[String] = createTopicProducer(test4Publisher)
    override def test5Topic(): ApiTopic[String] = createTopicProducer(test5Publisher)

    private def createTopicProducer(publisher: List[Promise[JPair[String, Offset]]]): ApiTopic[String] = {
      val eventStream =
        (_: Offset) => {
          val sources = publisher.map(promise => Source.fromFuture(promise.future))
          sources.foldLeft(Source.empty[JPair[String, Offset]])(_.concat(_)).asJava
        }

      topicProducer.singletonAtLeastOnce(eventStream.asJava)
    }
  }

  val testModule = new AbstractModule with ServiceGuiceSupport {
    override def configure(): Unit = {
      bindServices(serviceBinding(classOf[TestService], classOf[TestServiceImpl]))
    }
  }
  val noServiceLocatorModule = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[ServiceLocator]).toInstance(new NoServiceLocator)
    }
  }

  val offsetTrackerModule = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[OffsetTracker]).toInstance(InMemoryOffsetTracker)
    }
  }

  val otherDependenciesModule = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[PersistentEntityRegistry]).toInstance(NullPersistentEntityRegistry)
    }
  }

  /**
   * An implementation of the service locator that always fails to locate the passed service's `name`.
   */
  class NoServiceLocator extends ServiceLocator {

    override def locate(name: String, serviceCall: Descriptor.Call[_, _]): CompletionStage[Optional[URI]] =
      CompletableFuture.completedFuture(Optional.empty())

    override def doWithService[T](name: String, serviceCall: Descriptor.Call[_, _], block: JFunction[URI, CompletionStage[T]]): CompletionStage[Optional[T]] =
      CompletableFuture.completedFuture(Optional.empty())
  }

  object InMemoryOffsetTracker extends OffsetTracker {
    private val key2offsetDao = TrieMap.empty[String, OffsetTracker.OffsetDao]

    override def of(topicId: TopicId, info: ServiceInfo): Future[OffsetTracker.OffsetDao] = {
      val key = s"${topicId.value}-${info.serviceName()}"
      Future.successful(key2offsetDao.getOrElseUpdate(key, new InMemoryOffsetDao()))
    }

    @volatile var injectFailure = false
    class InMemoryOffsetDao extends OffsetTracker.OffsetDao {
      @volatile private var currentOffset = Offset.NONE
      def lastOffset: Offset = currentOffset
      def save(offset: Offset): Future[Done] = {
        if (injectFailure) throw new FakeCassandraException
        else {
          currentOffset = offset
          Future.successful(Done)
        }
      }
    }

    /** Exception to simulate a cassandra failure. */
    class FakeCassandraException extends RuntimeException
  }

  object NullPersistentEntityRegistry extends PersistentEntityRegistry {
    override def eventStream[Event <: AggregateEvent[Event]](aggregateTag: AggregateEventTag[Event], fromOffset: Offset): JSource[JPair[Event, Offset], NotUsed] =
      JSource.empty()

    override def gracefulShutdown(timeout: FiniteDuration): CompletionStage[Done] = CompletableFuture.completedFuture(Done.getInstance())

    override def refFor[C](entityClass: Class[_ <: com.lightbend.lagom.javadsl.persistence.PersistentEntity[C, _, _]], entityId: String): PersistentEntityRef[C] =
      ???

    override def register[C, E, S](entityClass: Class[_ <: com.lightbend.lagom.javadsl.persistence.PersistentEntity[C, E, S]]): Unit = ()
  }

}
