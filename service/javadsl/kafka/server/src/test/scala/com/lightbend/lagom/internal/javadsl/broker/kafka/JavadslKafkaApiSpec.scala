/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ CompletableFuture, CompletionStage, CountDownLatch, TimeUnit }

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.japi.{ Pair => JPair }
import akka.persistence.query.{ NoOffset, Offset, Sequence }
import akka.stream.javadsl.{ Source => JSource }
import akka.stream.scaladsl.{ Flow, Sink, Source, SourceQueue }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.{ Done, NotUsed }
import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.javadsl.broker.kafka.JavadslKafkaApiSpec._
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter.{ dslOffsetToOffset, offsetToDslOffset }
import com.lightbend.lagom.internal.kafka.KafkaLocalServer
import com.lightbend.lagom.javadsl.api.ScalaService._
import com.lightbend.lagom.javadsl.api._
import com.lightbend.lagom.javadsl.api.broker.kafka.{ KafkaProperties, PartitionKeyStrategy }
import com.lightbend.lagom.javadsl.api.broker.{ Message, Topic }
import com.lightbend.lagom.javadsl.broker.TopicProducer
import com.lightbend.lagom.javadsl.broker.kafka.KafkaMetadataKeys
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventTag, PersistentEntityRef, PersistentEntityRegistry, Offset => JOffset }
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
import com.lightbend.lagom.spi.persistence.{ InMemoryOffsetStore, OffsetStore }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder

import scala.collection.mutable
import scala.compat.java8.FunctionConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Promise }

class JavadslKafkaApiSpec extends WordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with ScalaFutures
  with OptionValues {

  private lazy val offsetStore = new InMemoryOffsetStore

  private val application = {
    new GuiceApplicationBuilder()
      .bindings(
        bind[OffsetStore].toInstance(offsetStore),
        bind[PersistentEntityRegistry].toInstance(NullPersistentEntityRegistry),
        JavadslKafkaApiSpec.testModule,
        bind[ServiceLocator].to[ConfigurationServiceLocator]
      )
      .configure(
        "akka.remote.netty.tcp.port" -> "0",
        "akka.remote.netty.tcp.hostname" -> "127.0.0.1",
        "akka.persistence.journal.plugin" -> "akka.persistence.journal.inmem",
        "akka.persistence.snapshot-store.plugin" -> "akka.persistence.snapshot-store.local",
        "lagom.services.kafka_native" -> s"tcp://localhost:${KafkaLocalServer.DefaultPort}"
      )
      .build()
  }

  private val kafkaServer = KafkaLocalServer(cleanOnStart = true)

  override def beforeAll(): Unit = {
    super.beforeAll()

    kafkaServer.start()

    val system = application.injector.instanceOf(classOf[ActorSystem])
    Cluster(system).join(Cluster(system).selfAddress)
  }

  before {
    // Reset the messageTransformer in case a previous test failed after setting it
    messageTransformer = identity
  }

  override def afterAll(): Unit = {
    application.stop().futureValue
    kafkaServer.stop()

    super.afterAll()
  }

  override implicit val patienceConfig = PatienceConfig(30.seconds, 150.millis)
  import application.materializer

  "The Kafka message broker api" should {

    val testService = application.injector.instanceOf(classOf[JavadslKafkaApiSpec.TestService])

    "eagerly publish event stream registered in the service topic implementation" in {
      val messageReceived = Promise[String]()
      testService.test1Topic()
        .subscribe()
        .withGroupId("testservice1")
        .atLeastOnce {
          Flow[String].map { message =>
            messageReceived.trySuccess(message)
            Done
          }.asJava
        }

      val messageToPublish = "msg"
      test1EventJournal.append(messageToPublish)

      messageReceived.future.futureValue shouldBe messageToPublish
    }

    "self-heal if failure occurs while running the publishing stream" in {
      // Create a subscriber that tracks the first two messages it receives
      val firstTimeReceived = Promise[String]()
      val secondTimeReceived = Promise[String]()
      testService.test2Topic()
        .subscribe()
        .withGroupId("testservice2")
        .atLeastOnce {
          Flow[String].map { message =>
            if (!firstTimeReceived.isCompleted) {
              firstTimeReceived.trySuccess(message)
            } else if (!secondTimeReceived.isCompleted)
              secondTimeReceived.trySuccess(message)
            else ()
            Done
          }.asJava
        }

      // Insert a mapping function into the producer flow that transforms each message
      val firstMessagePublishedSuccessfully = new CountDownLatch(1)
      messageTransformer = { message =>
        firstMessagePublishedSuccessfully.countDown()
        s"$message-transformed"
      }

      val firstMessageToPublish = "firstMessage"
      test2EventJournal.append(firstMessageToPublish)

      // Wait until first message is seen by the publisher
      assert(firstMessagePublishedSuccessfully.await(10, TimeUnit.SECONDS))
      // Ensure the transformed message is visible to the subscriber
      firstTimeReceived.future.futureValue shouldBe s"$firstMessageToPublish-transformed"

      // Now simulate a failure: this will result in an exception being
      // thrown before committing the offset of the next processed message.
      // It should retry automatically, which means it should throw the error
      // continuously until successful.
      val secondMessageTriggeredErrorTwice = new CountDownLatch(2)
      messageTransformer = { message =>
        secondMessageTriggeredErrorTwice.countDown()
        println(s"Expect to see an error below: Error processing message: [$message]")
        throw new RuntimeException(s"Error processing message: [$message]")
      }

      // Publish a second message.
      val secondMessageToPublish = "secondMessage"
      test2EventJournal.append(secondMessageToPublish)

      // Since the count-down happens before the error is thrown, trying
      // twice ensures that the first error was handled completely.
      assert(secondMessageTriggeredErrorTwice.await(30, TimeUnit.SECONDS))

      // After the exception was handled, remove the cause
      // of the failure and check that production resumes.
      val secondMessagePublishedSuccessfully = new CountDownLatch(1)
      messageTransformer = { message =>
        secondMessagePublishedSuccessfully.countDown()
        s"$message-transformed"
      }
      assert(secondMessagePublishedSuccessfully.await(60, TimeUnit.SECONDS))

      // The subscriber flow should be unaffected,
      // hence it will process the second message
      secondTimeReceived.future.futureValue shouldBe s"$secondMessageToPublish-transformed"
    }

    "keep track of the read-side offset when publishing events" in {
      implicit val ec = application.injector.instanceOf(classOf[ExecutionContext])
      def reloadOffset() =
        offsetStore.prepare("topicProducer-" + testService.test3Topic().topicId().value(), "singleton").futureValue

      // No message was consumed from this topic, hence we expect the last stored offset to be NoOffset
      val offsetDao = reloadOffset()
      val initialOffset = offsetDao.loadedOffset
      initialOffset shouldBe NoOffset

      // Fake setting an offset to simulate a topic that has been restarted
      offsetDao.saveOffset(Sequence(1))

      // Put some messages in the stream
      test3EventJournal.append("firstMessage")
      test3EventJournal.append("secondMessage")
      test3EventJournal.append("thirdMessage")

      // Wait for a subscriber to consume them all (which ensures they've all been published)
      val allMessagesReceived = new CountDownLatch(3)
      testService.test3Topic()
        .subscribe()
        .withGroupId("testservice3")
        .atLeastOnce {
          Flow[String].map { _ =>
            allMessagesReceived.countDown()
            Done
          }.asJava
        }
      assert(allMessagesReceived.await(10, TimeUnit.SECONDS))

      // After publishing all of the messages we expect the offset store
      // to have been updated with the offset of the last consumed message
      val updatedOffset = reloadOffset().loadedOffset
      updatedOffset shouldBe Sequence(2)
    }

    "self-heal at-least-once consumer stream if a failure occurs" in {
      val materialized = new CountDownLatch(2)

      @volatile var failOnMessageReceived = true
      testService.test4Topic()
        .subscribe()
        .withGroupId("testservice4")
        .atLeastOnce {
          Flow[String].map { _ =>
            if (failOnMessageReceived) {
              failOnMessageReceived = false
              println("Expect to see an error below: Simulate consumer failure")
              throw new IllegalStateException("Simulate consumer failure")
            } else Done
          }.mapMaterializedValue { _ =>
            materialized.countDown()
          }.asJava
        }

      test4EventJournal.append("message")

      // After throwing the error, the flow should be rematerialized, so consumption resumes
      assert(materialized.await(10, TimeUnit.SECONDS))
    }

    "self-heal at-most-once consumer stream if a failure occurs" in {
      case object SimulateFailure extends RuntimeException

      // Let's publish a message to the topic
      test5EventJournal.append("message")

      // Now we register a consumer that will fail while processing a message. Because we are using at-most-once
      // delivery, the message that caused the failure won't be re-processed.
      @volatile var countProcessedMessages = 0
      val expectFailure = testService.test5Topic()
        .subscribe()
        .withGroupId("testservice5")
        .atMostOnceSource()
        .asScala
        .via {
          Flow[String].map { _ =>
            countProcessedMessages += 1
            throw SimulateFailure
          }
        }
        .runWith(Sink.ignore)

      expectFailure.failed.futureValue shouldBe an[SimulateFailure.type]
      countProcessedMessages shouldBe 1
    }

    "allow the consumer to batch" in {
      val batchSize = 4
      val latch = new CountDownLatch(batchSize)
      testService.test6Topic()
        .subscribe()
        .atLeastOnce {
          Flow[String].grouped(batchSize).mapConcat { messages =>
            messages.map { _ =>
              latch.countDown()
              Done
            }
          }.asJava
        }
      for (i <- 1 to batchSize) test6EventJournal.append(i.toString)
      assert(latch.await(10, TimeUnit.SECONDS))
    }

    "attach metadata to the message" in {
      test7EventJournal.append("A1")
      test7EventJournal.append("A2")
      test7EventJournal.append("A3")

      val messages = Await.result(
        testService.test7Topic().subscribe.withMetadata.atMostOnceSource.asScala.take(3).runWith(Sink.seq),
        10.seconds
      )

      messages.size shouldBe 3
      def runAssertions(msg: Message[String]): Unit = {
        msg.messageKeyAsString shouldBe "A"
        msg.get(KafkaMetadataKeys.TOPIC).asScala.value shouldBe "test7"
        msg.get(KafkaMetadataKeys.HEADERS) should not be None
        msg.get(KafkaMetadataKeys.PARTITION).asScala.value shouldBe
          messages.head.get(KafkaMetadataKeys.PARTITION).asScala.value
      }
      messages.foreach(runAssertions)
      messages.head.getPayload shouldBe "A1"
      val offset = messages.head.get(KafkaMetadataKeys.OFFSET).asScala.value
      messages(1).getPayload shouldBe "A2"
      messages(1).get(KafkaMetadataKeys.OFFSET).asScala.value shouldBe (offset + 1)
      messages(2).getPayload shouldBe "A3"
      messages(2).get(KafkaMetadataKeys.OFFSET).asScala.value shouldBe (offset + 2)
    }

  }

}

object JavadslKafkaApiSpec {

  private val test1EventJournal = new EventJournal[String]
  private val test2EventJournal = new EventJournal[String]
  private val test3EventJournal = new EventJournal[String]
  private val test4EventJournal = new EventJournal[String]
  private val test5EventJournal = new EventJournal[String]
  private val test6EventJournal = new EventJournal[String]
  private val test7EventJournal = new EventJournal[String]

  // Allows tests to insert logic into the producer stream
  @volatile var messageTransformer: String => String = identity

  trait TestService extends Service {
    def test1Topic(): Topic[String]
    def test2Topic(): Topic[String]
    def test3Topic(): Topic[String]
    def test4Topic(): Topic[String]
    def test5Topic(): Topic[String]
    def test6Topic(): Topic[String]
    def test7Topic(): Topic[String]

    override def descriptor(): Descriptor =
      named("testservice")
        .withTopics(
          topic("test1", test1Topic _),
          topic("test2", test2Topic _),
          topic("test3", test3Topic _),
          topic("test4", test4Topic _),
          topic("test5", test5Topic _),
          topic("test6", test6Topic _),
          topic("test7", test7Topic _)
            .withProperty(KafkaProperties.partitionKeyStrategy(), new PartitionKeyStrategy[String] {
              override def computePartitionKey(message: String) = message.take(1)
            })
        )
  }

  trait TestEvent extends AggregateEvent[TestEvent]

  class TestServiceImpl extends TestService {
    override def test1Topic(): Topic[String] = createTopicProducer(test1EventJournal)
    override def test2Topic(): Topic[String] = createTopicProducer(test2EventJournal)
    override def test3Topic(): Topic[String] = createTopicProducer(test3EventJournal)
    override def test4Topic(): Topic[String] = createTopicProducer(test4EventJournal)
    override def test5Topic(): Topic[String] = createTopicProducer(test5EventJournal)
    override def test6Topic(): Topic[String] = createTopicProducer(test6EventJournal)
    override def test7Topic(): Topic[String] = createTopicProducer(test7EventJournal)

    private def createTopicProducer(eventJournal: EventJournal[String]): Topic[String] =
      TopicProducer.singleStreamWithOffset[String]({ fromOffset: JOffset =>
        eventJournal
          .eventStream(dslOffsetToOffset(fromOffset))
          .map(element => new JPair(messageTransformer(element._1), offsetToDslOffset(element._2)))
          .asJava
      }.asJava)
  }

  val testModule = new AbstractModule with ServiceGuiceSupport {
    override def configure(): Unit = {
      bindService(classOf[TestService], classOf[TestServiceImpl])
    }
  }

  class EventJournal[Event] {
    private type Element = (Event, Sequence)
    private val offset = new AtomicLong()
    private val storedEvents = mutable.MutableList.empty[Element]
    private val subscribers = mutable.MutableList.empty[SourceQueue[Element]]

    def eventStream(fromOffset: Offset): Source[(Event, Offset), _] = {
      val minOffset: Long = fromOffset match {
        case Sequence(value) => value
        case NoOffset        => -1
        case _               => throw new IllegalArgumentException(s"Sequence offset required, but got $fromOffset")
      }

      Source.queue[Element](8, OverflowStrategy.fail)
        .mapMaterializedValue { queue =>
          synchronized {
            storedEvents.foreach(queue.offer)
            subscribers += queue
          }
          NotUsed
        }
        // Skip everything up and including the fromOffset provided
        .dropWhile(_._2.value <= minOffset)
    }

    def append(event: Event): Unit = {
      val element = (event, Sequence(offset.getAndIncrement()))
      synchronized {
        storedEvents += element
        subscribers.foreach(_.offer(element))
      }
    }
  }

  object NullPersistentEntityRegistry extends PersistentEntityRegistry {
    override def eventStream[Event <: AggregateEvent[Event]](aggregateTag: AggregateEventTag[Event], fromOffset: JOffset): JSource[JPair[Event, JOffset], NotUsed] =
      JSource.empty()

    override def gracefulShutdown(timeout: FiniteDuration): CompletionStage[Done] = CompletableFuture.completedFuture(Done.getInstance())

    override def refFor[C](entityClass: Class[_ <: com.lightbend.lagom.javadsl.persistence.PersistentEntity[C, _, _]], entityId: String): PersistentEntityRef[C] =
      ???

    override def register[C, E, S](entityClass: Class[_ <: com.lightbend.lagom.javadsl.persistence.PersistentEntity[C, E, S]]): Unit = ()
  }

}
