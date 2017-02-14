/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import akka.util.ByteString
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import org.apache.kafka.common.serialization.{ Deserializer, Serializer }

/**
 * Adapts a Lagom NegotiatedDeserializer into a Kafka Deserializer so that messages
 * stored in Kafka can be deserialized into the expected application's type.
 */
private[lagom] class JavadslKafkaDeserializer[T](deserializer: NegotiatedDeserializer[T, ByteString]) extends Deserializer[T] {

  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {
    () // ignore
  }

  override def deserialize(topic: String, data: Array[Byte]): T =
    deserializer.deserialize(ByteString(data))

  override def close(): Unit = () // nothing to do

}

/**
 * Adapts a Lagom NegotiatedSerializer into a Kafka Serializer so that application's
 * messages can be serialized into a byte array and published into Kafka.
 */
private[lagom] class JavadslKafkaSerializer[T](serializer: NegotiatedSerializer[T, ByteString]) extends Serializer[T] {

  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {
    () // ignore
  }

  override def serialize(topic: String, data: T): Array[Byte] =
    serializer.serialize(data).toArray

  override def close(): Unit = () // nothing to do

}
