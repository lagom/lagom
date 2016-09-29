/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka.serializer

import org.apache.kafka.common.serialization.Deserializer
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.NegotiatedDeserializer
import akka.util.ByteString

/**
 * Adapts a Lagom NegotiatedDeserializer into a Kafka Deserializer so that messages
 * stored in Kafka can be deserialized into the expected application's type.
 */
class KafkaDeserializer[T](deserializer: NegotiatedDeserializer[T, ByteString]) extends Deserializer[T] {

  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {
    () // ignore
  }

  override def deserialize(topic: String, data: Array[Byte]): T =
    deserializer.deserialize(ByteString(data))

  override def close(): Unit = () // nothing to do

}
