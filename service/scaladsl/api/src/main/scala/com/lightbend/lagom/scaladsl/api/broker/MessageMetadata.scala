/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.broker

/**
 * A message broker message.
 *
 * This provides access to both the message payload, and the metadata.
 */
sealed trait Message[Payload] {
  /**
   * The payload of the message.
   */
  def payload: Payload

  /**
   * Get the metadata for the given metadata key.
   *
   * @param key The key of the metadata.
   * @return The metadata, if found for that key.
   */
  def get[Metadata](key: MetadataKey[Metadata]): Option[Metadata]

  /**
   * Add a metadata key and value to this message.
   *
   * @param keyValue The key and value to add.
   * @return A copy of this message with the key and value added.
   */
  def +[Metadata](keyValue: (MetadataKey[Metadata], Metadata)): Message[Payload]

  /**
   * Return a copy of this message with the given payload.
   *
   * @param payload The payload.
   * @return A copy of this message with the given payload.
   */
  def withPayload[P2](payload: P2): Message[P2]

  /**
   * Get the message key as a String.
   *
   * If the key is not a String, it will be converted to a String using `toString`.
   *
   * If the underlying message broker doesn't support keys, the empty string will be returned.
   *
   * @return A string representation of the message key.
   */
  def messageKeyAsString: String = get(MetadataKey.MessageKey[Any]).fold("")(_.toString)
}

object Message {
  /**
   * Create a message with the given payload.
   */
  def apply[Payload](payload: Payload): Message[Payload] = {
    MessageImpl(payload, Map.empty)
  }

  private case class MessageImpl[Payload](payload: Payload, metadataMap: Map[MetadataKey[_], _]) extends Message[Payload] {
    override def get[Metadata](key: MetadataKey[Metadata]): Option[Metadata] = {
      metadataMap.get(key).asInstanceOf[Option[Metadata]]
    }
    override def +[Metadata](keyValue: (MetadataKey[Metadata], Metadata)): Message[Payload] = {
      MessageImpl(payload, metadataMap + keyValue)
    }

    override def withPayload[P2](payload: P2): Message[P2] = MessageImpl(payload, metadataMap)
  }
}

/**
 * A metadata key.
 */
sealed trait MetadataKey[Metadata] {
  /**
   * The name of the metadata key.
   */
  def name: String
}

object MetadataKey {

  /**
   * Create a metadata key with the given name.
   */
  def apply[Metadata](name: String): MetadataKey[Metadata] = {
    MetadataKeyImpl(name)
  }

  private case class MetadataKeyImpl[Metadata](name: String) extends MetadataKey[Metadata]

  /**
   * The message key metadata key.
   */
  def MessageKey[Key]: MetadataKey[Key] = MessageKeyObj.asInstanceOf[MetadataKey[Key]]

  private val MessageKeyObj = MetadataKey[Any]("messageKey")
}