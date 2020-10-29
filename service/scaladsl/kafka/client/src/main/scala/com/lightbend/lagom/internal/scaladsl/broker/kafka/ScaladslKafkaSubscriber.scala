/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.broker.kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.pattern.BackoffOpts
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.api.UriUtils
import com.lightbend.lagom.internal.broker.kafka.ConsumerConfig
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig
import com.lightbend.lagom.internal.broker.kafka.KafkaSubscriberActor
import com.lightbend.lagom.internal.broker.kafka.NoKafkaBrokersException
import com.lightbend.lagom.scaladsl.api.Descriptor.TopicCall
import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.broker.Message
import com.lightbend.lagom.scaladsl.api.broker.MetadataKey
import com.lightbend.lagom.scaladsl.api.broker.Subscriber
import com.lightbend.lagom.scaladsl.broker.kafka.KafkaMetadataKeys
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

/**
 * A Consumer for consuming messages from Kafka using the Alpakka Kafka API.
 */
private[lagom] class ScaladslKafkaSubscriber[Payload, SubscriberPayload](
    kafkaConfig: KafkaConfig,
    topicCall: TopicCall[Payload],
    groupId: Subscriber.GroupId,
    info: ServiceInfo,
    system: ActorSystem,
    serviceLocator: ServiceLocator,
    transform: ConsumerRecord[String, Payload] => SubscriberPayload
)(implicit mat: Materializer, ec: ExecutionContext)
    extends Subscriber[SubscriberPayload] {
  private val log = LoggerFactory.getLogger(classOf[ScaladslKafkaSubscriber[_, _]])

  import ScaladslKafkaSubscriber._

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
    else new ScaladslKafkaSubscriber(kafkaConfig, topicCall, newGroupId, info, system, serviceLocator, transform)
  }

  override def withMetadata = new ScaladslKafkaSubscriber[Payload, Message[SubscriberPayload]](
    kafkaConfig,
    topicCall,
    groupId,
    info,
    system,
    serviceLocator,
    wrapPayload
  )

  private def wrapPayload(record: ConsumerRecord[String, Payload]): Message[SubscriberPayload] = {
    Message(transform(record)) +
      (MetadataKey.MessageKey[String]  -> record.key()) +
      (KafkaMetadataKeys.Offset        -> record.offset()) +
      (KafkaMetadataKeys.Partition     -> record.partition()) +
      (KafkaMetadataKeys.Topic         -> record.topic()) +
      (KafkaMetadataKeys.Headers       -> record.headers()) +
      (KafkaMetadataKeys.Timestamp     -> record.timestamp()) +
      (KafkaMetadataKeys.TimestampType -> record.timestampType())
  }

  private def consumerSettings = {
    val keyDeserializer = new StringDeserializer
    val valueDeserializer = {
      val messageSerializer = topicCall.messageSerializer
      val protocol          = messageSerializer.serializerForRequest.protocol
      val deserializer      = messageSerializer.deserializer(protocol)
      new ScaladslKafkaDeserializer(deserializer)
    }

    val config = system.settings.config.getConfig(ConsumerSettings.configPath)
    ConsumerSettings(config, keyDeserializer, valueDeserializer)
      .withBootstrapServers(kafkaConfig.brokers)
      .withGroupId(groupId.groupId)
      // Consumer must have a unique clientId otherwise a javax.management.InstanceAlreadyExistsException is thrown
      .withClientId(s"${info.serviceName}-$consumerId")
  }

  private def subscription = Subscriptions.topics(
    kafkaConfig.topicNameMapping.getOrElse(topicCall.topicId.name, topicCall.topicId.name)
  )

  override def atMostOnceSource: Source[SubscriberPayload, _] = {
    kafkaConfig.serviceName match {
      case Some(name) =>
        log.debug("Creating at most once source using service locator to look up Kafka services at {}", name)
        Source
          .single(())
          .mapAsync(1)(_ => serviceLocator.locateAll(name))
          .flatMapConcat {
            case Nil =>
              throw new NoKafkaBrokersException(name)

            case uris =>
              val endpoints = UriUtils.hostAndPorts(uris)
              log.debug("Connecting to Kafka service named {} at {}", name: Any, endpoints)
              Consumer
                .atMostOnceSource(
                  consumerSettings.withBootstrapServers(endpoints),
                  subscription
                )
                .map(transform)
          }

      case None =>
        log.debug("Creating at most once source with configured brokers: {}", kafkaConfig.brokers)
        Consumer
          .atMostOnceSource(consumerSettings, subscription)
          .map(transform)
    }
  }

  override def atLeastOnce(flow: Flow[SubscriberPayload, Done, _]): Future[Done] = {
    val streamCompleted = Promise[Done]
    val consumerProps =
      KafkaSubscriberActor.props[Payload, SubscriberPayload](
        kafkaConfig,
        consumerConfig,
        serviceLocator.locateAll,
        topicCall.topicId.name,
        flow,
        consumerSettings,
        subscription,
        streamCompleted,
        transform
      )

    val backoffConsumerProps =
      BackoffOpts
        .onStop(
          consumerProps,
          s"KafkaConsumerActor$consumerId-${topicCall.topicId.name}",
          consumerConfig.minBackoff,
          consumerConfig.maxBackoff,
          consumerConfig.randomBackoffFactor
        )
        .withDefaultStoppingStrategy

    system.actorOf(
      BackoffSupervisor.props(backoffConsumerProps),
      s"KafkaBackoffConsumer$consumerId-${topicCall.topicId.name}"
    )

    streamCompleted.future
  }
}

private[lagom] object ScaladslKafkaSubscriber {
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

    def default(info: ServiceInfo): GroupId = GroupId(info.serviceName)
  }
}
