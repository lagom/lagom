/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.broker.kafka

import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.DiscoverySupport
import akka.pattern.BackoffOpts
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Source
import com.lightbend.lagom.internal.broker.kafka.ConsumerConfig
import com.lightbend.lagom.internal.broker.kafka.KafkaSubscriberActor
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Message
import com.lightbend.lagom.javadsl.api.broker.MetadataKey
import com.lightbend.lagom.javadsl.api.broker.Subscriber
import com.lightbend.lagom.javadsl.broker.kafka.KafkaMetadataKeys
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise

/**
 * A Consumer for consuming messages from Kafka using the Alpakka Kafka API.
 */
private[lagom] class JavadslKafkaSubscriber[Payload, SubscriberPayload](
    topicCall: TopicCall[Payload],
    groupId: Subscriber.GroupId,
    info: ServiceInfo,
    system: ActorSystem,
    transform: ConsumerRecord[String, Payload] => SubscriberPayload
)(implicit mat: Materializer, ec: ExecutionContext)
    extends Subscriber[SubscriberPayload] {
  private val log = LoggerFactory.getLogger(classOf[JavadslKafkaSubscriber[_, _]])

  import JavadslKafkaSubscriber._

  private lazy val consumerId = KafkaClientIdSequenceNumber.getAndIncrement

  private def consumerConfig = ConsumerConfig(system)

  @throws(classOf[IllegalArgumentException])
  override def withGroupId(groupIdName: String): Subscriber[SubscriberPayload] = {
    val newGroupId = {
      if (groupIdName == null) {
        // An empty group id is not allowed by Kafka (see https://issues.apache.org/jira/browse/KAFKA-2648
        // and https://github.com/akka/alpakka-kafka/issues/155)
        val defaultGroupId = GroupId.default(info)
        log.debug {
          "Passed a null groupId, but Kafka requires clients to set one (see KAFKA-2648). " +
            s"Defaulting $this consumer groupId to $defaultGroupId."
        }
        defaultGroupId
      } else GroupId(groupIdName)
    }

    if (newGroupId == groupId) this
    else new JavadslKafkaSubscriber(topicCall, newGroupId, info, system, transform)
  }

  override def withMetadata() = new JavadslKafkaSubscriber(
    topicCall,
    groupId,
    info,
    system,
    wrapPayload
  )

  private def wrapPayload(record: ConsumerRecord[String, Payload]): Message[SubscriberPayload] = {
    Message
      .create(transform(record))
      .add(MetadataKey.messageKey[String], record.key())
      .add(KafkaMetadataKeys.OFFSET, record.offset().asInstanceOf[java.lang.Long])
      .add(KafkaMetadataKeys.PARTITION, record.partition().asInstanceOf[java.lang.Integer])
      .add(KafkaMetadataKeys.TOPIC, record.topic())
      .add(KafkaMetadataKeys.HEADERS, record.headers())
      .add(KafkaMetadataKeys.TIMESTAMP, record.timestamp().asInstanceOf[java.lang.Long])
      .add(KafkaMetadataKeys.TIMESTAMP_TYPE, record.timestampType())
  }

  private def consumerSettings = {
    val keyDeserializer = new StringDeserializer
    val valueDeserializer = {
      val messageSerializer = topicCall.messageSerializer()
      val protocol          = messageSerializer.serializerForRequest().protocol()
      val deserializer      = messageSerializer.deserializer(protocol)
      new JavadslKafkaDeserializer(deserializer)
    }

    val config = system.settings.config.getConfig(ConsumerSettings.configPath)
    ConsumerSettings(config, keyDeserializer, valueDeserializer)
      .withGroupId(groupId.groupId())
      // Consumer must have a unique clientId otherwise a javax.management.InstanceAlreadyExistsException is thrown
      .withClientId(s"${info.serviceName()}-$consumerId")
      .withEnrichAsync(
        DiscoverySupport.consumerBootstrapServers(config)(system)
      )
  }

  private def subscription = Subscriptions.topics(topicCall.topicId().value)

  override def atMostOnceSource: Source[SubscriberPayload, _] = {
    Consumer
      .atMostOnceSource(consumerSettings, subscription)
      .map(transform)
      .asJava
  }

  override def atLeastOnce(flow: Flow[SubscriberPayload, Done, _]): CompletionStage[Done] = {
    val streamCompleted = Promise[Done]
    val consumerProps =
      KafkaSubscriberActor.props(
        consumerConfig,
        topicCall.topicId().value(),
        flow.asScala,
        consumerSettings,
        subscription,
        streamCompleted,
        transform
      )

    val backoffConsumerProps =
      BackoffOpts
        .onStop(
          consumerProps,
          s"KafkaConsumerActor$consumerId-${topicCall.topicId().value}",
          consumerConfig.minBackoff,
          consumerConfig.maxBackoff,
          consumerConfig.randomBackoffFactor
        )
        .withDefaultStoppingStrategy

    system.actorOf(
      BackoffSupervisor.props(backoffConsumerProps),
      s"KafkaBackoffConsumer$consumerId-${topicCall.topicId().value}"
    )

    streamCompleted.future.toJava
  }
}

private[lagom] object JavadslKafkaSubscriber {
  private val KafkaClientIdSequenceNumber = new AtomicInteger(1)

  case class GroupId(groupId: String) extends Subscriber.GroupId {
    if (GroupId.isInvalidGroupId(groupId))
      throw new IllegalArgumentException(
        s"Failed to create group because [groupId=$groupId] contains invalid character(s). Check the Kafka spec for creating a valid group id."
      )
  }
  case object GroupId {
    private val InvalidGroupIdChars =
      Set('/', '\\', ',', '\u0000', ':', '"', '\'', ';', '*', '?', ' ', '\t', '\r', '\n', '=')
    // based on https://github.com/apache/kafka/blob/623ab1e7c6497c000bc9c9978637f20542a3191c/core/src/test/scala/unit/kafka/common/ConfigTest.scala#L60
    private def isInvalidGroupId(groupId: String): Boolean = groupId.exists(InvalidGroupIdChars.apply)

    def default(info: ServiceInfo): GroupId = GroupId(info.serviceName())
  }
}
