/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import java.net.URI
import java.util.Optional
import java.util.concurrent.{ CompletableFuture, CompletionStage, CountDownLatch, TimeUnit }
import java.util.function.{ Function => JFunction }

import scala.collection.concurrent.TrieMap
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
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.japi.{ Pair => JPair }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.javadsl.{ Source => JSource }
import akka.stream.scaladsl.{ Flow, Sink, Source, SourceQueueWithComplete }
import akka.testkit.EventFilter
import com.lightbend.lagom.internal.javadsl.broker.kafka.JavadslKafkaApiSpec.{ InMemoryOffsetStore, NoServiceLocator, NullPersistentEntityRegistry }
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.javadsl.broker.TopicProducer
import com.lightbend.lagom.spi.persistence.{ OffsetDao, OffsetStore }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject._

class JavadslKafkaApiSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  private val application = {
    new GuiceApplicationBuilder()
      .bindings(
        bind[OffsetStore].toInstance(InMemoryOffsetStore),
        bind[PersistentEntityRegistry].toInstance(NullPersistentEntityRegistry),
        JavadslKafkaApiSpec.testModule,
        bind[ServiceLocator].to[NoServiceLocator]
      ).configure(
          "akka.remote.netty.tcp.port" -> "0",
          "akka.remote.netty.tcp.hostname" -> "127.0.0.1",
          "akka.persistence.journal.plugin" -> "akka.persistence.journal.inmem",
          "akka.persistence.snapshot-store.plugin" -> "akka.persistence.snapshot-store.local"
        ).build()
  }

  @volatile private var kafkaServer = KafkaLocalServer(cleanOnStart = true)

  override def beforeAll() = {
    kafkaServer.start()
    val system = application.injector.instanceOf(classOf[ActorSystem])
    Cluster(system).join(Cluster(system).selfAddress)
  }

  override def afterAll() = {
    application.stop().futureValue
    kafkaServer.stop()
  }

  implicit val patience = PatienceConfig(30.seconds, 150.millis)

  "The Kafka message broker api" should {

    val testService = application.injector.instanceOf(classOf[JavadslKafkaApiSpec.TestService])

    "eagerly publish event stream registered in the service topic implementation" in {
      val queue = JavadslKafkaApiSpec.test1Queue.futureValue
      val testTopic = testService.test1Topic()

      val messageReceived = Promise[String]()
      testTopic.subscribe().atLeastOnce(Flow.fromFunction { (message: String) =>
        messageReceived.trySuccess(message)
        Done
      }.asJava)

      val messageToPublish = "msg"
      queue.offer(new JPair(messageToPublish, Offset.NONE))

      messageToPublish shouldBe messageReceived.future.futureValue
    }

    "self-heal if failure occurs while running the publishing stream" in {
      implicit val system = application.injector.instanceOf(classOf[ActorSystem])
      val queue = JavadslKafkaApiSpec.test2Queue.futureValue
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
      queue.offer(new JPair(firstMessageToPublish, Offset.NONE))

      // wait until first message is received
      firstMessageToPublish shouldBe firstTimeReceived.future.futureValue

      // now simulate a failure: this will results in an exception being thrown when committing
      // the offset of the first processed message.
      EventFilter[JavadslKafkaApiSpec.InMemoryOffsetStore.FakeCassandraException]() intercept {
        JavadslKafkaApiSpec.InMemoryOffsetStore.injectFailure = true
        // Let the flow processing continue. A failure is expected to occur when publishing the next message.
        messageCommitFailureInjected.countDown()
      }

      // After the exception was logged, let's remove the cause of the failure and check the stream computation resumes.
      JavadslKafkaApiSpec.InMemoryOffsetStore.injectFailure = false

      // publish a second message.
      val secondMessageToPublish = "secondMessage"
      queue.offer(new JPair(secondMessageToPublish, Offset.NONE))

      // The subscriber flow should have self-healed and hence it will process the second message (this time, succeeding)
      secondMessageToPublish shouldBe secondTimeReceived.future.futureValue
    }

    "keep track of the read-side offset when publishing events" in {
      implicit val ec = application.injector.instanceOf(classOf[ExecutionContext])
      val info = application.injector.instanceOf(classOf[ServiceInfo])
      val queue = JavadslKafkaApiSpec.test3Queue.futureValue
      val testTopic = testService.test3Topic()

      def trackedOffset = JavadslKafkaApiSpec.InMemoryOffsetStore.prepare("topicProducer-" + testTopic.topicId().value(), "singleton")
        .map(dao => OffsetAdapter.offsetToDslOffset(dao.loadedOffset)).futureValue

      // No message was consumed from this topic, hence we expect the last stored offset to be NONE
      trackedOffset shouldBe Offset.NONE

      val messageReceived = Promise[String]()
      testTopic.subscribe().atLeastOnce(Flow.fromFunction { (message: String) =>
        messageReceived.trySuccess(message)
        Done
      }.asJava)

      val messageToPublish = "msg"
      val messageOffset = Offset.sequence(1)
      queue.offer(new JPair(messageToPublish, messageOffset))
      messageReceived.future.futureValue shouldBe messageToPublish

      // After consuming a message we expect the offset store to have been updated with the offset of
      // the consumed message
      trackedOffset shouldBe messageOffset
    }

    "self-heal at-least-once consumer stream if a failure occurs" in {
      val queue = JavadslKafkaApiSpec.test4Queue.futureValue
      val testTopic = testService.test4Topic()
      val materialized = new CountDownLatch(2)

      @volatile var failOnMessageReceived = true
      testTopic.subscribe().atLeastOnce(Flow.fromFunction { (message: String) =>
        if (failOnMessageReceived) {
          failOnMessageReceived = false
          throw new IllegalStateException("Simulate consumer failure")
        } else Done
      }.mapMaterializedValue(_ => materialized.countDown()).asJava)

      val messageSent = "msg"
      queue.offer(new JPair(messageSent, Offset.NONE))

      // After throwing the error, the flow should be rematerialized, so consumption resumes
      materialized.await(10, TimeUnit.SECONDS)
    }

    "self-heal at-most-once consumer stream if a failure occurs" in {
      implicit val mat = application.injector.instanceOf(classOf[Materializer])
      implicit val ec = application.injector.instanceOf(classOf[ExecutionContext])
      case object SimulateFailure extends RuntimeException

      val queue = JavadslKafkaApiSpec.test5Queue.futureValue
      val testTopic = testService.test5Topic()

      // Let's publish a messages to the topic
      val message = "message"
      queue.offer(new JPair(message, Offset.NONE))

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

object JavadslKafkaApiSpec {

  private val (test1Source, test1Queue) = publisher
  private val (test2Source, test2Queue) = publisher
  private val (test3Source, test3Queue) = publisher
  private val (test4Source, test4Queue) = publisher
  private val (test5Source, test5Queue) = publisher

  private def publisher = {
    val promise = Promise[SourceQueueWithComplete[JPair[String, Offset]]]()
    val source = Source.queue[JPair[String, Offset]](8, OverflowStrategy.fail)
      .mapMaterializedValue(queue => promise.trySuccess(queue))

    (source, promise.future)
  }

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

  trait TestEvent extends AggregateEvent[TestEvent]

  class TestServiceImpl extends TestService {
    override def test1Topic(): ApiTopic[String] = createTopicProducer(test1Source)
    override def test2Topic(): ApiTopic[String] = createTopicProducer(test2Source)
    override def test3Topic(): ApiTopic[String] = createTopicProducer(test3Source)
    override def test4Topic(): ApiTopic[String] = createTopicProducer(test4Source)
    override def test5Topic(): ApiTopic[String] = createTopicProducer(test5Source)

    private def createTopicProducer(publisher: Source[JPair[String, Offset], _]): ApiTopic[String] = {
      TopicProducer.singleStreamWithOffset(new JFunction[Offset, JSource[JPair[String, Offset], Any]] {
        def apply(offset: Offset) = publisher.asJava
      })
    }
  }

  val testModule = new AbstractModule with ServiceGuiceSupport {
    override def configure(): Unit = {
      bindServices(serviceBinding(classOf[TestService], classOf[TestServiceImpl]))
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

  object InMemoryOffsetStore extends OffsetStore {
    private val offsets = TrieMap.empty[String, Offset]

    override def prepare(eventProcessorId: String, tag: String): Future[OffsetDao] = {
      val key = s"$eventProcessorId-$tag"
      val offset = offsets.getOrElseUpdate(key, Offset.NONE)
      Future.successful(new InMemoryOffsetDao(key, OffsetAdapter.dslOffsetToOffset(offset)))
    }

    @volatile var injectFailure = false
    class InMemoryOffsetDao(key: String, override val loadedOffset: akka.persistence.query.Offset) extends OffsetDao {
      override def saveOffset(offset: akka.persistence.query.Offset): Future[Done] = {
        if (injectFailure) throw new FakeCassandraException
        else {
          offsets.put(key, OffsetAdapter.offsetToDslOffset(offset))
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
