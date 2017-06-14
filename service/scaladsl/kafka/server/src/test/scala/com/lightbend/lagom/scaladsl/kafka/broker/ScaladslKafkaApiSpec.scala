/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.kafka.broker

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import akka.Done
import akka.cluster.Cluster
import akka.persistence.query.{ NoOffset, Offset, Sequence }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source, SourceQueueWithComplete }
import akka.testkit.EventFilter
import com.lightbend.lagom.internal.kafka.KafkaLocalServer
import com.lightbend.lagom.internal.kafka.KafkaLocalServer.ZooKeeperLocalServer
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service }
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.client.ConfigurationServiceLocatorComponents
import com.lightbend.lagom.scaladsl.kafka.broker.ScaladslKafkaApiSpec.{ InMemoryOffsetStore, TestService, TestServiceImpl }
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.spi.persistence.{ OffsetDao, OffsetStore }
import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import org.I0Itec.zkclient.ZkClient
import org.I0Itec.zkclient.exception.ZkMarshallingError
import org.I0Itec.zkclient.serialize.ZkSerializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import play.api.Configuration
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

class ScaladslKafkaApiSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  private val application = {
    new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents with LagomKafkaComponents with ConfigurationServiceLocatorComponents {
      override lazy val offsetStore = InMemoryOffsetStore
      override lazy val lagomServer = serverFor[TestService](new TestServiceImpl)

      override def additionalConfiguration = super.additionalConfiguration ++ Configuration.from(Map(
        "akka.remote.netty.tcp.port" -> "0",
        "akka.remote.netty.tcp.hostname" -> "127.0.0.1",
        "akka.persistence.journal.plugin" -> "akka.persistence.journal.inmem",
        "akka.persistence.snapshot-store.plugin" -> "akka.persistence.snapshot-store.local",
        "lagom.services.kafka_native" -> s"tcp://localhost:${KafkaLocalServer.DefaultPort}"
      ))

      lazy val testService = serviceClient.implement[TestService]
    }
  }

  import application.{ executionContext, materializer }

  private val kafkaServer = KafkaLocalServer(cleanOnStart = true)

  override def beforeAll(): Unit = {
    super.beforeAll()

    kafkaServer.start()

    KafkaSpecTools.createTopics((1 to ScaladslKafkaApiSpec.topicCount).map(i => s"test$i"))

    Cluster(application.actorSystem).join(Cluster(application.actorSystem).selfAddress)
  }

  override def afterAll(): Unit = {
    application.application.stop().futureValue
    kafkaServer.stop()

    super.afterAll()
  }

  implicit val patience = PatienceConfig(30.seconds, 150.millis)

  "The Kafka message broker api" should {

    val testService = application.testService

    "eagerly publish event stream registered in the service topic implementation" in {
      val queue = ScaladslKafkaApiSpec.test1Queue.futureValue

      val messageReceived = Promise[String]()
      testService.test1Topic.subscribe.withGroupId("testservice1").atLeastOnce(Flow[String].map { message =>
        messageReceived.trySuccess(message)
        Done
      })

      val messageToPublish = "msg"
      queue.offer((messageToPublish, NoOffset))

      messageToPublish shouldBe messageReceived.future.futureValue
    }

    "self-heal if failure occurs while running the publishing stream" in {
      implicit val system = application.actorSystem
      val queue = ScaladslKafkaApiSpec.test2Queue.futureValue

      val firstTimeReceived = Promise[String]()
      val secondTimeReceived = Promise[String]()
      val messageCommitFailureInjected = new CountDownLatch(1)

      testService.test2Topic.subscribe.withGroupId("testservice2").atLeastOnce(Flow[String].map { message =>
        if (!firstTimeReceived.isCompleted) {
          firstTimeReceived.trySuccess(message)
          messageCommitFailureInjected.await()
        } else if (!secondTimeReceived.isCompleted)
          secondTimeReceived.trySuccess(message)
        else ()
        Done
      })

      val firstMessageToPublish = "firstMessage"
      queue.offer((firstMessageToPublish, NoOffset))

      // wait until first message is received
      firstMessageToPublish shouldBe firstTimeReceived.future.futureValue

      // now simulate a failure: this will results in an exception being thrown when committing
      // the offset of the first processed message.
      EventFilter[ScaladslKafkaApiSpec.InMemoryOffsetStore.FakeCassandraException]() intercept {
        ScaladslKafkaApiSpec.InMemoryOffsetStore.injectFailure = true
        // Let the flow processing continue. A failure is expected to occur when publishing the next message.
        messageCommitFailureInjected.countDown()
      }

      // After the exception was logged, let's remove the cause of the failure and check the stream computation resumes.
      ScaladslKafkaApiSpec.InMemoryOffsetStore.injectFailure = false

      // publish a second message.
      val secondMessageToPublish = "secondMessage"
      queue.offer((secondMessageToPublish, NoOffset))

      // The subscriber flow should have self-healed and hence it will process the second message (this time, succeeding)
      secondMessageToPublish shouldBe secondTimeReceived.future.futureValue

    }

    "keep track of the read-side offset when publishing events" in {
      val queue = ScaladslKafkaApiSpec.test3Queue.futureValue

      def trackedOffset = ScaladslKafkaApiSpec.InMemoryOffsetStore
        .prepare("topicProducer-" + testService.test3Topic.topicId.name, "singleton")
        .map(_.loadedOffset).futureValue

      // No message was consumed from this topic, hence we expect the last stored offset to be NONE
      trackedOffset shouldBe NoOffset

      val messageReceived = Promise[String]()
      testService.test3Topic.subscribe.withGroupId("testservice3").atLeastOnce(Flow[String].map { message =>
        messageReceived.trySuccess(message)
        Done
      })

      val messageToPublish = "msg"
      val messageOffset = Sequence(1)
      queue.offer((messageToPublish, messageOffset))
      messageReceived.future.futureValue shouldBe messageToPublish

      // After consuming a message we expect the offset store to have been updated with the offset of
      // the consumed message
      trackedOffset shouldBe messageOffset
    }

    "self-heal at-least-once consumer stream if a failure occurs" in {
      val queue = ScaladslKafkaApiSpec.test4Queue.futureValue
      val materialized = new CountDownLatch(2)

      @volatile var failOnMessageReceived = true
      testService.test4Topic.subscribe.withGroupId("testservice4").atLeastOnce(Flow[String].map { message =>
        if (failOnMessageReceived) {
          failOnMessageReceived = false
          throw new IllegalStateException("Simulate consumer failure")
        } else Done
      }.mapMaterializedValue(_ => materialized.countDown()))

      val messageSent = "msg"
      queue.offer((messageSent, NoOffset))

      // After throwing the error, the flow should be rematerialized, so consumption resumes
      materialized.await(10, TimeUnit.SECONDS)
    }

    "self-heal at-most-once consumer stream if a failure occurs" in {
      case object SimulateFailure extends RuntimeException

      val queue = ScaladslKafkaApiSpec.test5Queue.futureValue

      // Let's publish a messages to the topic
      val message = "message"
      queue.offer((message, NoOffset))

      // Now we register a consumer that will fail while processing a message. Because we are using at-most-once
      // delivery, the message that caused the failure won't be re-processed.
      @volatile var countProcessedMessages = 0
      val expectFailure = testService.test5Topic.subscribe.withGroupId("testservice5").atMostOnceSource
        .via(Flow[String].map { message =>
          countProcessedMessages += 1
          throw SimulateFailure
        })
        .runWith(Sink.ignore)

      expectFailure.failed.futureValue shouldBe an[SimulateFailure.type]
      countProcessedMessages shouldBe 1
    }

    "allow the consumer to batch" in {
      val batchSize = 4
      val latch = new CountDownLatch(batchSize)
      testService.test6Topic.subscribe.atLeastOnce(
        Flow[String].grouped(batchSize).mapConcat { messages =>
          messages.map { _ =>
            latch.countDown()
            Done
          }
        }
      )
      val queue = ScaladslKafkaApiSpec.test6Queue.futureValue
      for (i <- 1 to batchSize) queue.offer((i.toString, NoOffset))
      latch.await(10, TimeUnit.SECONDS) shouldBe true
    }
  }

}

object KafkaSpecTools {

  // copy pasted from Kafka sources to make it visible (in Kafka that is pakage private
  private[this] object StringSerializer extends ZkSerializer {

    @throws(classOf[ZkMarshallingError])
    def serialize(data: Object): Array[Byte] = data.asInstanceOf[String].getBytes("UTF-8")

    @throws(classOf[ZkMarshallingError])
    def deserialize(bytes: Array[Byte]): Object = {
      if (bytes == null)
        null
      else
        new String(bytes, "UTF-8")
    }
  }

  def createTopics(topics: Seq[String]): Any = {
    // based on https://stackoverflow.com/a/23360100/103190
    val zkClient = new ZkClient(s"localhost:${ZooKeeperLocalServer.DefaultPort}", 1000, 1000, StringSerializer)
    val zkUtils: kafka.utils.ZkUtils = ZkUtils(zkClient, isZkSecurityEnabled = false)
    topics.foreach(t =>
      AdminUtils.createTopic(zkUtils, t, 1, 1))
    zkClient.close()
  }

}

object ScaladslKafkaApiSpec {

  val topicCount = 6 // this value is used in beforeAll to ensure all topics exist in Kafka.

  private val (test1Source, test1Queue) = publisher
  private val (test2Source, test2Queue) = publisher
  private val (test3Source, test3Queue) = publisher
  private val (test4Source, test4Queue) = publisher
  private val (test5Source, test5Queue) = publisher
  private val (test6Source, test6Queue) = publisher

  private def publisher = {
    val promise = Promise[SourceQueueWithComplete[(String, Offset)]]()
    val source = Source.queue[(String, Offset)](8, OverflowStrategy.fail)
      .mapMaterializedValue(queue => promise.trySuccess(queue))

    (source, promise.future)
  }

  trait TestService extends Service {
    def test1Topic: Topic[String]

    def test2Topic: Topic[String]

    def test3Topic: Topic[String]

    def test4Topic: Topic[String]

    def test5Topic: Topic[String]

    def test6Topic: Topic[String]

    import Service._

    override def descriptor: Descriptor = {
      named("testservice")
        .withTopics(
          topic("test1", test1Topic),
          topic("test2", test2Topic),
          topic("test3", test3Topic),
          topic("test4", test4Topic),
          topic("test5", test5Topic),
          topic("test6", test6Topic)
        )
    }
  }

  trait TestEvent extends AggregateEvent[TestEvent]

  class TestServiceImpl extends TestService {
    override def test1Topic = createTopicProducer(test1Source)

    override def test2Topic = createTopicProducer(test2Source)

    override def test3Topic = createTopicProducer(test3Source)

    override def test4Topic = createTopicProducer(test4Source)

    override def test5Topic = createTopicProducer(test5Source)

    override def test6Topic = createTopicProducer(test6Source)

    private def createTopicProducer(publisher: Source[(String, Offset), _]): Topic[String] = {
      TopicProducer.singleStreamWithOffset(offset => publisher)
    }
  }

  object InMemoryOffsetStore extends OffsetStore {
    private val offsets = TrieMap.empty[String, Offset]

    override def prepare(eventProcessorId: String, tag: String): Future[OffsetDao] = {
      val key = s"$eventProcessorId-$tag"
      val offset = offsets.getOrElseUpdate(key, NoOffset)
      Future.successful(new InMemoryOffsetDao(key, offset))
    }

    @volatile var injectFailure = false

    class InMemoryOffsetDao(key: String, override val loadedOffset: Offset) extends OffsetDao {
      override def saveOffset(offset: Offset): Future[Done] = {
        if (injectFailure) throw new FakeCassandraException
        else {
          offsets.put(key, offset)
          Future.successful(Done)
        }
      }
    }

    /** Exception to simulate a cassandra failure. */
    class FakeCassandraException extends RuntimeException

  }

}
