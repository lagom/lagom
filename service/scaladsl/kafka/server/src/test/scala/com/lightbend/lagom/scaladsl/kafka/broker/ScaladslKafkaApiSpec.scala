/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.kafka.broker

import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import akka.cluster.Cluster
import akka.persistence.query.NoOffset
import akka.persistence.query.Offset
import akka.persistence.query.Sequence
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueue
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.devmode.MiniLogger
import com.lightbend.lagom.devmode.Servers.KafkaServer
import com.lightbend.lagom.scaladsl.api.broker.Message
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.scaladsl.api.broker.kafka.PartitionKeyStrategy
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.broker.TopicProducerCommand
import com.lightbend.lagom.scaladsl.broker.kafka.KafkaMetadataKeys
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.client.ConfigurationServiceLocatorComponents
import com.lightbend.lagom.scaladsl.kafka.broker.ScaladslKafkaApiSpec._
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.spi.persistence.InMemoryOffsetStore
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._
import org.slf4j.LoggerFactory
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ScaladslKafkaApiSpec
    extends AnyWordSpecLike
    with Matchers
    with BeforeAndAfter
    with BeforeAndAfterAll
    with ScalaFutures
    with Eventually
    with OptionValues {
  private val log = LoggerFactory.getLogger(getClass)
  private val miniLogger = new MiniLogger {
    def debug(message: => String): Unit = log.debug(message)
    def info(message: => String): Unit  = log.info(message)
  }

  implicit override val patienceConfig = PatienceConfig(30.seconds, 150.millis)

  private final val kafkaPort          = 9092
  private final val kafkaZooKeeperPort = 2181

  private val application = {
    new LagomApplication(LagomApplicationContext.Test)
      with AhcWSComponents
      with LagomKafkaComponents
      with ConfigurationServiceLocatorComponents {
      override lazy val offsetStore            = new InMemoryOffsetStore
      override lazy val jsonSerializerRegistry = EmptyJsonSerializerRegistry
      override lazy val lagomServer            = serverFor[TestService](new TestServiceImpl)

      override def additionalConfiguration = {
        import scala.collection.JavaConverters._
        super.additionalConfiguration ++ ConfigFactory.parseMap(
          Map(
            "akka.remote.artery.canonical.port"             -> "0",
            "akka.remote.artery.canonical.hostname"         -> "127.0.0.1",
            "akka.persistence.journal.plugin"               -> "akka.persistence.journal.inmem",
            "akka.persistence.snapshot-store.plugin"        -> "akka.persistence.snapshot-store.local",
            "lagom.cluster.exit-jvm-when-system-terminated" -> "off",
            "lagom.cluster.bootstrap.enabled"               -> "off",
            "lagom.services.kafka_native"                   -> s"tcp://localhost:$kafkaPort"
          ).asJava
        )
      }

      lazy val testService = serviceClient.implement[TestService]
    }
  }

  import application.materializer

  private val kafkaServerClasspath: Seq[File] = TestBuildInfo.fullClasspath.toIndexedSeq
  private var kafkaServer: Option[Closeable]  = None

  override def beforeAll(): Unit = {
    super.beforeAll()

    kafkaServer = Some(
      KafkaServer.start(
        log = miniLogger,
        cp = kafkaServerClasspath,
        kafkaPort = kafkaPort,
        zooKeeperPort = kafkaZooKeeperPort,
        kafkaPropertiesFile = None,
        jvmOptions = Nil,
        targetDir = TestBuildInfo.target,
        cleanOnStart = true,
      )
    )

    Cluster(application.actorSystem).join(Cluster(application.actorSystem).selfAddress)
  }

  before {
    // Reset the messageTransformer in case a previous test failed after setting it
    messageTransformer = identity
  }

  override def afterAll(): Unit = {
    application.application.stop().futureValue

    kafkaServer.foreach(_.close())
    kafkaServer = None

    super.afterAll()
  }

  "The Kafka message broker api" should {
    import scala.language.reflectiveCalls
    val testService = application.testService

    "eagerly publish event stream registered in the service topic implementation" in {
      val messageReceived = Promise[String]()
      testService.test1Topic.subscribe
        .withGroupId("testservice1")
        .atLeastOnce {
          Flow[String].map { message =>
            messageReceived.trySuccess(message)
            Done
          }
        }

      val messageToPublish = "msg"
      test1EventJournal.append(messageToPublish)

      messageReceived.future.futureValue shouldBe messageToPublish
    }

    "self-heal if failure occurs while running the publishing stream" in {
      // Create a subscriber that tracks the first two messages it receives
      val firstTimeReceived  = Promise[String]()
      val secondTimeReceived = Promise[String]()
      testService.test2Topic.subscribe
        .withGroupId("testservice2")
        .atLeastOnce {
          Flow[String].map { message =>
            if (!firstTimeReceived.isCompleted) {
              firstTimeReceived.trySuccess(message)
            } else if (!secondTimeReceived.isCompleted)
              secondTimeReceived.trySuccess(message)
            else ()
            Done
          }
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
      def reloadOffset() =
        application.offsetStore.prepare("topicProducer-" + testService.test3Topic.topicId.name, "singleton").futureValue

      // No message was consumed from this topic, hence we expect the last stored offset to be NoOffset
      val offsetDao     = reloadOffset()
      val initialOffset = offsetDao.loadedOffset
      initialOffset shouldBe NoOffset

      // Put some messages in the stream
      test3EventJournal.append(("firstMessage", true))
      test3EventJournal.append(("secondMessage", false))
      test3EventJournal.append(("thirdMessage", true))

      // Wait for a subscriber to consume them all (which ensures they've all been published)
      val allMessagesReceived = new CountDownLatch(2)
      testService.test3Topic.subscribe
        .withGroupId("testservice3")
        .atLeastOnce {
          Flow[String].map { _ =>
            allMessagesReceived.countDown()
            Done
          }
        }
      assert(allMessagesReceived.await(10, TimeUnit.SECONDS))

      // After publishing all of the messages we expect the offset store
      // to have been updated with the offset of the last published message
      eventually(timeout(10.seconds)) {
        val updatedOffset = reloadOffset().loadedOffset
        updatedOffset shouldBe Sequence(2)
      }
    }

    "self-heal at-least-once consumer stream if a failure occurs" in {
      val materialized = new CountDownLatch(2)

      @volatile var failOnMessageReceived = true
      testService.test4Topic.subscribe
        .withGroupId("testservice4")
        .atLeastOnce {
          Flow[String]
            .map { _ =>
              if (failOnMessageReceived) {
                failOnMessageReceived = false
                println("Expect to see an error below: Simulate consumer failure")
                throw new IllegalStateException("Simulate consumer failure")
              } else Done
            }
            .mapMaterializedValue { _ =>
              materialized.countDown()
            }
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
      val expectFailure = testService.test5Topic.subscribe
        .withGroupId("testservice5")
        .atMostOnceSource
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
      val latch     = new CountDownLatch(batchSize)
      testService.test6Topic.subscribe
        .withGroupId("testservice6")
        .atLeastOnce {
          Flow[String].grouped(batchSize).mapConcat { messages =>
            messages.map { _ =>
              latch.countDown()
              Done
            }
          }
        }
      for (i <- 1 to batchSize) test6EventJournal.append(i.toString)
      assert(latch.await(10, TimeUnit.SECONDS))
    }

    "attach metadata to the message" in {
      test7EventJournal.append("A1")
      test7EventJournal.append("A2")
      test7EventJournal.append("A3")

      val messages = Await.result(
        testService.test7Topic.subscribe.withMetadata.atMostOnceSource.take(3).runWith(Sink.seq),
        10.seconds
      )

      messages.size shouldBe 3
      def runAssertions(msg: Message[String]): Unit = {
        msg.messageKeyAsString shouldBe "A"
        msg.get(KafkaMetadataKeys.Topic).value shouldBe "test7"
        msg.get(KafkaMetadataKeys.Headers) should not be None
        msg.get(KafkaMetadataKeys.Timestamp) should not be None
        msg.get(KafkaMetadataKeys.TimestampType) should not be None
        msg.get(KafkaMetadataKeys.Partition).value shouldBe messages.head.get(KafkaMetadataKeys.Partition).value
      }
      messages.foreach(runAssertions)
      messages.head.payload shouldBe "A1"
      val offset = messages.head.get(KafkaMetadataKeys.Offset).value
      messages(1).payload shouldBe "A2"
      messages(1).get(KafkaMetadataKeys.Offset).value shouldBe (offset + 1)
      messages(2).payload shouldBe "A3"
      messages(2).get(KafkaMetadataKeys.Offset).value shouldBe (offset + 2)
    }
  }
}

object ScaladslKafkaApiSpec {
  private val test1EventJournal = new EventJournal[String]
  private val test2EventJournal = new EventJournal[String]
  private val test3EventJournal = new EventJournal[(String, Boolean)]
  private val test4EventJournal = new EventJournal[String]
  private val test5EventJournal = new EventJournal[String]
  private val test6EventJournal = new EventJournal[String]
  private val test7EventJournal = new EventJournal[String]

  // Allows tests to insert logic into the producer stream
  @volatile var messageTransformer: String => String = identity

  trait TestService extends Service {
    def test1Topic: Topic[String]
    def test2Topic: Topic[String]
    def test3Topic: Topic[String]
    def test4Topic: Topic[String]
    def test5Topic: Topic[String]
    def test6Topic: Topic[String]
    def test7Topic: Topic[String]

    import Service._

    override def descriptor: Descriptor = {
      named("testservice")
        .withTopics(
          topic("test1", test1Topic),
          topic("test2", test2Topic),
          topic("test3", test3Topic),
          topic("test4", test4Topic),
          topic("test5", test5Topic),
          topic("test6", test6Topic),
          topic("test7", test7Topic)
            .addProperty(
              KafkaProperties.partitionKeyStrategy,
              PartitionKeyStrategy[String](_.take(1))
            )
        )
    }
  }

  trait TestEvent extends AggregateEvent[TestEvent]

  class TestServiceImpl extends TestService {
    override def test1Topic: Topic[String] = createTopicProducer(test1EventJournal)
    override def test2Topic: Topic[String] = createTopicProducer(test2EventJournal)
    override def test3Topic: Topic[String] = createTopicProducerWithCommand(test3EventJournal)
    override def test4Topic: Topic[String] = createTopicProducer(test4EventJournal)
    override def test5Topic: Topic[String] = createTopicProducer(test5EventJournal)
    override def test6Topic: Topic[String] = createTopicProducer(test6EventJournal)
    override def test7Topic: Topic[String] = createTopicProducer(test7EventJournal)

    private def createTopicProducer(eventJournal: EventJournal[String]): Topic[String] = {
      TopicProducer.singleStreamWithOffset { fromOffset =>
        eventJournal
          .eventStream(fromOffset)
          .map(element => (messageTransformer(element._1), element._2))
      }
    }

    private def createTopicProducerWithCommand(eventJournal: EventJournal[(String, Boolean)]): Topic[String] = {
      TopicProducer.singleCommandStreamWithOffset { fromOffset =>
        eventJournal
          .eventStream(fromOffset)
          .map {
            case ((message, emit), offset) if emit =>
              new TopicProducerCommand.EmitAndCommit(messageTransformer(message), offset)
            case (_, offset) => new TopicProducerCommand.Commit(offset)
          }
      }
    }
  }

  class EventJournal[Event] {
    private type Element = (Event, Sequence)
    private val offset       = new AtomicLong()
    private val storedEvents = mutable.ListBuffer.empty[Element]
    private val subscribers  = mutable.ListBuffer.empty[SourceQueue[Element]]

    def eventStream(fromOffset: Offset): Source[(Event, Offset), _] = {
      val minOffset: Long = fromOffset match {
        case Sequence(value) => value
        case NoOffset        => -1
        case _               => throw new IllegalArgumentException(s"Sequence offset required, but got $fromOffset")
      }

      Source
        .queue[Element](8, OverflowStrategy.fail)
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
}
