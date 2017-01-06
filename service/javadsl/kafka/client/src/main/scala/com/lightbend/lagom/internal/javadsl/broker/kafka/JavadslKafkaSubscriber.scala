/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.{ ActorSystem, SupervisorStrategy }
import akka.kafka.{ ConsumerSettings, Subscriptions }
import akka.kafka.scaladsl.Consumer
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import akka.stream.javadsl.{ Flow, Source }
import com.lightbend.lagom.internal.broker.kafka.{ ConsumerConfig, KafkaConfig, KafkaSubscriberActor }
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Subscriber
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Promise }

import scala.compat.java8.FutureConverters._

/**
 * A Consumer for consuming messages from Kafka using the akka-stream-kafka API.
 */
private[lagom] class JavadslKafkaSubscriber[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message],
                                                     groupId: Subscriber.GroupId, info: ServiceInfo, system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) extends Subscriber[Message] {
  private val log = LoggerFactory.getLogger(classOf[JavadslKafkaSubscriber[_]])

  import JavadslKafkaSubscriber._

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
    else new JavadslKafkaSubscriber(kafkaConfig, topicCall, newGroupId, info, system)
  }

  private def consumerSettings = {
    val keyDeserializer = new StringDeserializer
    val valueDeserializer = {
      val messageSerializer = topicCall.messageSerializer()
      val protocol = messageSerializer.serializerForRequest().protocol()
      val deserializer = messageSerializer.deserializer(protocol)
      new JavadslKafkaDeserializer(deserializer)
    }

    ConsumerSettings(system, keyDeserializer, valueDeserializer)
      .withBootstrapServers(kafkaConfig.brokers)
      .withGroupId(groupId.groupId())
      // Consumer must have a unique clientId otherwise a javax.management.InstanceAlreadyExistsException is thrown
      .withClientId(s"${info.serviceName()}-$consumerId")
  }

  private def subscription = Subscriptions.topics(topicCall.topicId().value)

  override def atMostOnceSource: Source[Message, _] = {
    Consumer.atMostOnceSource(consumerSettings, subscription)
      .map(_.value)
      .asJava
  }

  override def atLeastOnce(flow: Flow[Message, Done, _]): CompletionStage[Done] = {
    val streamCompleted = Promise[Done]
    val consumerProps = KafkaSubscriberActor.props(consumerConfig, topicCall.topicId().value(), flow.asScala,
      consumerSettings, subscription, streamCompleted)

    val backoffConsumerProps = BackoffSupervisor.propsWithSupervisorStrategy(
      consumerProps, s"KafkaConsumerActor$consumerId-${topicCall.topicId().value}", consumerConfig.minBackoff,
      consumerConfig.maxBackoff, consumerConfig.randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )

    system.actorOf(backoffConsumerProps, s"KafkaBackoffConsumer$consumerId-${topicCall.topicId().value}")

    streamCompleted.future.toJava
  }

}

private[lagom] object JavadslKafkaSubscriber {
  private val KafkaClientIdSequenceNumber = new AtomicInteger(1)

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

}
