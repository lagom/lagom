/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

import com.lightbend.lagom.internal.broker.kafka.serializer.KafkaSerializer
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.broker.Publisher
import com.lightbend.lagom.javadsl.api.broker.modules.Properties.Property
import com.lightbend.lagom.javadsl.broker.kafka.Properties.PARTITION_KEY_STRATEGY
import com.lightbend.lagom.javadsl.broker.kafka.property.PartitionKeyStrategy

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.{ Producer => ReactiveProducer }
import akka.stream.Materializer
import akka.stream.javadsl.{ Source => JSource }

/**
 * A Producer for publishing messages in Kafka using the akka-stream-kafka API.
 */
class Producer[Message] private (config: KafkaConfig, topicCall: TopicCall[Message], system: ActorSystem)(implicit mat: Materializer) extends Publisher[Message] {

  private val log = LoggerFactory.getLogger(classOf[Producer[_]])

  private def producerSettings: ProducerSettings[String, Message] = {
    val keySerializer = new StringSerializer
    val valueSerializer = new KafkaSerializer(topicCall.messageSerializer().serializerForRequest())

    ProducerSettings(system, keySerializer, valueSerializer)
      .withBootstrapServers(config.brokers)
  }

  override def publish(messages: JSource[Message, _]): Unit = {
    val property = PARTITION_KEY_STRATEGY.asInstanceOf[Property[PartitionKeyStrategy[Message]]]
    val strategy = topicCall.properties().getValueOf(property)
    def keyOf(message: Message): String = {
      if (strategy == null) null
      else strategy.computePartitionKey(message)
    }

    messages
      .asScala
      .map((message: Message) => new ProducerRecord[String, Message](topicCall.topicId.value, keyOf(message), message))
      .runWith(ReactiveProducer.plainSink(producerSettings))
  }
}

object Producer {
  def apply[Message](config: KafkaConfig, topicCall: TopicCall[Message], system: ActorSystem, mat: Materializer): Producer[Message] =
    new Producer(config, topicCall, system)(mat)
}
