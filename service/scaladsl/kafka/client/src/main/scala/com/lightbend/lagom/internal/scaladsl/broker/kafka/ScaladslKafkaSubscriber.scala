/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.broker.kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.{ ActorSystem, SupervisorStrategy }
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ ConsumerSettings, Subscriptions }
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Source }
import com.lightbend.lagom.internal.broker.kafka.{ ConsumerConfig, KafkaConfig, KafkaSubscriberActor }
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.broker.Subscriber
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * A Consumer for consuming messages from Kafka using the akka-stream-kafka API.
 */
private[lagom] class ScaladslKafkaSubscriber[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message],
                                                      groupId: Subscriber.GroupId, info: ServiceInfo, system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) extends Subscriber[Message] {
  private val log = LoggerFactory.getLogger(classOf[ScaladslKafkaSubscriber[_]])

  import ScaladslKafkaSubscriber._

  private lazy val consumerId = KafkaClientIdSequenceNumber.getAndIncrement

  private def consumerConfig = ConsumerConfig(system.settings.config)

  @throws(classOf[IllegalArgumentException])
  override def withGroupId(groupId: String): Subscriber[Message] = {
    val newGroupId = {
      if (groupId == null) {
        // An empty group id is not allowed by Kafka (see https://issues.apache.org/jira/browse/KAFKA-2648
        // and https://github.com/akka/reactive-kafka/issues/155)
        val defaultGroupId = GroupId.default(info)
        log.debug {
          "Passed a null groupId, but Kafka requires clients to set one (see KAFKA-2648). " +
            s"Defaulting $this consumer groupId to $defaultGroupId."
        }
        defaultGroupId
      } else GroupId(groupId)
    }

    if (newGroupId.groupId == groupId) this
    else new ScaladslKafkaSubscriber(kafkaConfig, topicCall, newGroupId, info, system)
  }

  private def consumerSettings = {
    val keyDeserializer = new StringDeserializer
    val valueDeserializer = {
      val messageSerializer = topicCall.messageSerializer
      val protocol = messageSerializer.serializerForRequest.protocol
      val deserializer = messageSerializer.deserializer(protocol)
      new ScaladslKafkaDeserializer(deserializer)
    }

    ConsumerSettings(system, keyDeserializer, valueDeserializer)
      .withBootstrapServers(kafkaConfig.brokers)
      .withGroupId(groupId.groupId)
      // Consumer must have a unique clientId otherwise a javax.management.InstanceAlreadyExistsException is thrown
      .withClientId(s"${info.serviceName}-$consumerId")
  }

  private def subscription = Subscriptions.topics(topicCall.topicId.name)

  override def atMostOnceSource: Source[Message, _] = {
    Consumer.atMostOnceSource(consumerSettings, subscription)
      .map(_.value)
  }

  override def atLeastOnce(flow: Flow[Message, Done, _]): Future[Done] = {
    val streamCompleted = Promise[Done]
    val consumerProps = KafkaSubscriberActor.props(consumerConfig, topicCall.topicId.name, flow, consumerSettings,
      subscription, streamCompleted)

    val backoffConsumerProps = BackoffSupervisor.propsWithSupervisorStrategy(
      consumerProps, s"KafkaConsumerActor$consumerId-${topicCall.topicId.name}", consumerConfig.minBackoff,
      consumerConfig.maxBackoff, consumerConfig.randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )

    system.actorOf(backoffConsumerProps, s"KafkaBackoffConsumer$consumerId-${topicCall.topicId.name}")

    streamCompleted.future
  }

}

private[lagom] object ScaladslKafkaSubscriber {
  private val KafkaClientIdSequenceNumber = new AtomicInteger(1)

  case class GroupId(groupId: String) extends Subscriber.GroupId {
    if (GroupId.isInvalidGroupId(groupId))
      throw new IllegalArgumentException(s"Failed to create group because [groupId=$groupId] contains invalid character(s). Check the Kafka spec for creating a valid group id.")
  }
  case object GroupId {
    private val InvalidGroupIdChars = Set('/', '\\', ',', '\u0000', ':', '"', '\'', ';', '*', '?', ' ', '\t', '\r', '\n', '=')
    // based on https://github.com/apache/kafka/blob/623ab1e7c6497c000bc9c9978637f20542a3191c/core/src/test/scala/unit/kafka/common/ConfigTest.scala#L60
    private def isInvalidGroupId(groupId: String): Boolean = groupId.exists(InvalidGroupIdChars.apply)

    def default(info: ServiceInfo): GroupId = GroupId(info.serviceName)
  }

}
