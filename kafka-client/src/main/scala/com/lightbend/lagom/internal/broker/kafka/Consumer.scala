/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, Status, SupervisorStrategy }
import akka.kafka.ConsumerMessage.{ CommittableOffset, CommittableOffsetBatch }
import akka.kafka.scaladsl.{ Consumer => ReactiveConsumer }
import akka.kafka.{ AutoSubscription, ConsumerSettings, Subscriptions }
import akka.pattern.{ BackoffSupervisor, pipe }
import akka.stream.javadsl.{ Flow => JFlow, Source => JSource }
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, Sink, Source, Unzip, Zip }
import akka.stream.{ FlowShape, KillSwitch, KillSwitches, Materializer }
import com.lightbend.lagom.internal.broker.kafka.serializer.KafkaDeserializer
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Subscriber
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Promise }

/**
 * A Consumer for consuming messages from Kafka using the akka-stream-kafka API.
 */
class Consumer[Message] private (kafkaConfig: KafkaConfig, topicCall: TopicCall[Message], groupId: Subscriber.GroupId, info: ServiceInfo, system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) extends Subscriber[Message] {
  private val log = LoggerFactory.getLogger(classOf[Consumer[_]])

  private lazy val consumerId = Consumer.KafkaClientIdSequenceNumber.getAndIncrement

  private def consumerConfig = ConsumerConfig(system.settings.config)

  @throws(classOf[IllegalArgumentException])
  override def withGroupId(groupId: String): Subscriber[Message] = {
    val newGroupId = {
      if (groupId == null) {
        // An empty group id is not allowed by Kafka (see https://issues.apache.org/jira/browse/KAFKA-2648
        // and https://github.com/akka/reactive-kafka/issues/155)
        val defaultGroupId = Consumer.GroupId.default(info)
        log.debug {
          "Passed a null groupId, but Kafka requires clients to set one (see KAFKA-2648). " +
            s"Defaulting $this consumer groupId to $defaultGroupId."
        }
        defaultGroupId
      } else Consumer.GroupId(groupId)
    }

    if (newGroupId.groupId == groupId) this
    else new Consumer(kafkaConfig, topicCall, newGroupId, info, system)
  }

  private def consumerSettings = {
    val keyDeserializer = new StringDeserializer
    val valueDeserializer = {
      val messageSerializer = topicCall.messageSerializer()
      val protocol = messageSerializer.serializerForRequest().protocol()
      val deserializer = messageSerializer.deserializer(protocol)
      new KafkaDeserializer(deserializer)
    }

    ConsumerSettings(system, keyDeserializer, valueDeserializer)
      .withBootstrapServers(kafkaConfig.brokers)
      .withGroupId(groupId.groupId())
      // Consumer must have a unique clientId otherwise a javax.management.InstanceAlreadyExistsException is thrown 
      .withClientId(s"${info.serviceName()}-$consumerId")
  }

  private def subscription = Subscriptions.topics(topicCall.topicId().value)

  override def atMostOnceSource: JSource[Message, _] = {
    ReactiveConsumer.atMostOnceSource(consumerSettings, subscription)
      .map(_.value)
      .asJava
  }

  override def atLeastOnce(flow: JFlow[Message, Done, _]): CompletionStage[Done] = {
    val streamCompleted = Promise[Done]
    val consumerProps = Consumer.ConsumerActor.props(consumerConfig, topicCall.topicId(), flow, consumerSettings, subscription, streamCompleted)

    val backoffConsumerProps = BackoffSupervisor.propsWithSupervisorStrategy(
      consumerProps, s"KafkaConsumerActor$consumerId-${topicCall.topicId().value}", consumerConfig.minBackoff, consumerConfig.maxBackoff, consumerConfig.randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )

    system.actorOf(backoffConsumerProps, s"KafkaBackoffConsumer$consumerId-${topicCall.topicId().value}")

    streamCompleted.future.toJava
  }

}

object Consumer {
  private val KafkaClientIdSequenceNumber = new AtomicInteger(1)

  def apply[Message](config: KafkaConfig, topicCall: TopicCall[Message], info: ServiceInfo, system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) =
    new Consumer(config, topicCall, GroupId.default(info), info, system)

  case class GroupId(groupId: String) extends Subscriber.GroupId {
    if (GroupId.isInvalidGroupId(groupId))
      throw new IllegalArgumentException(s"Failed to create group because [groupId=$groupId] contains invalid character(s). Check the Kafka spec for creating a valid group id.")
  }
  case object GroupId {
    private val InvalidGroupIdChars = Set('/', '\\', ',', '\u0000', ':', '"', '\'', ';', '*', '?', ' ', '\t', '\r', '\n', '=')
    // based on https://github.com/apache/kafka/blob/623ab1e7c6497c000bc9c9978637f20542a3191c/core/src/test/scala/unit/kafka/common/ConfigTest.scala#L60
    private def isInvalidGroupId(groupId: String): Boolean = groupId.exists(InvalidGroupIdChars.apply)

    def default(info: ServiceInfo): GroupId = GroupId(info.serviceName())
  }

  private class ConsumerActor[Message](
    consumerConfig: ConsumerConfig, topicId: TopicId, flow: JFlow[Message, Done, _],
    consumerSettings: ConsumerSettings[String, Message], subscription: AutoSubscription,
    streamCompleted: Promise[Done]
  )(implicit mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

    /** Switch used to terminate the on-going Kafka publishing stream when this actor fails.*/
    private var shutdown: Option[KillSwitch] = None

    override def preStart(): Unit = {
      val (killSwitch, streamDone) =
        atLeastOnce(flow)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(Sink.ignore)(Keep.both)
          .run()

      shutdown = Some(killSwitch)
      streamDone pipeTo self
    }

    override def postStop(): Unit = {
      shutdown.foreach(_.shutdown())
    }

    override def receive: Actor.Receive = {
      case Status.Failure(e) =>
        throw e

      case Done =>
        log.info("Kafka consumer stream for topic {} was completed.", topicId)
        streamCompleted.success(Done)
        context.stop(self)
    }

    private def atLeastOnce(flow: JFlow[Message, Done, _]): Source[Done, _] = {
      // Creating a Source of pair where the first element is a reactive-kafka committable offset, 
      // and the second it's the actual message. Then, the source of pair is splitted into 
      // two streams, so that the `flow` passed in argument can be applied to the underlying message.
      // After having applied the `flow`, the two streams are combined back and the processed message's
      // offset is committed to Kafka.
      val pairedCommittableSource = ReactiveConsumer.committableSource(consumerSettings, subscription)
        .map(committableMessage => (committableMessage.committableOffset, committableMessage.record.value))

      val committOffsetFlow = Flow.fromGraph(GraphDSL.create(flow) { implicit builder => flow =>
        import GraphDSL.Implicits._
        val unzip = builder.add(Unzip[CommittableOffset, Message])
        val zip = builder.add(Zip[CommittableOffset, Done])
        val committer = {
          val commitFlow = Flow[(CommittableOffset, Done)]
            .groupedWithin(consumerConfig.batchingSize, consumerConfig.batchingInterval)
            .map(group => group.foldLeft(CommittableOffsetBatch.empty) { (batch, elem) => batch.updated(elem._1) })
            // parallelism set to 3 for no good reason other than because the akka team has seen good throughput with this value
            .mapAsync(parallelism = 3)(_.commitScaladsl())
          builder.add(commitFlow)
        }

        unzip.out0 ~> zip.in0
        unzip.out1 ~> flow ~> zip.in1
        zip.out ~> committer.in

        FlowShape(unzip.in, committer.out)
      })

      pairedCommittableSource.via(committOffsetFlow)
    }
  }

  object ConsumerActor {
    def props[Message](
      consumerConfig: ConsumerConfig, topicId: TopicId, flow: JFlow[Message, Done, _],
      consumerSettings: ConsumerSettings[String, Message], subscription: AutoSubscription,
      streamCompleted: Promise[Done]
    )(implicit mat: Materializer, ec: ExecutionContext) =
      Props(new ConsumerActor[Message](consumerConfig, topicId, flow, consumerSettings, subscription, streamCompleted))
  }
}
