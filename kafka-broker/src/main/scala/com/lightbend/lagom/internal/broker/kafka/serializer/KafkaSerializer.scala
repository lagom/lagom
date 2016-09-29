/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka.serializer

import org.apache.kafka.common.serialization.Serializer
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.NegotiatedSerializer
import akka.util.ByteString

/**
 * Adapts a Lagom NegotiatedSerializer into a Kafka Serializer so that application's
 * messages can be serialized into a byte array and published into Kafka.
 */
class KafkaSerializer[T](serializer: NegotiatedSerializer[T, ByteString]) extends Serializer[T] {

  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {
    () // ignore
  }

  override def serialize(topic: String, data: T): Array[Byte] =
    serializer.serialize(data).toArray

  override def close(): Unit = () // nothing to do

}
