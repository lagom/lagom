/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.api.broker

/**
 * A message broker message.
 *
 * This provides access to both the message payload, and the metadata.
 */
trait MessageWithMetadata[Payload] {

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
  def getMetadata[Metadata](key: MessageMetadataKey[Metadata]): Option[Metadata]

  /**
   * Return a copy of this message with the given payload.
   *
   * @param payload The payload.
   * @return A copy of this message with the given payload.
   */
  def withPayload[P2](payload: P2): MessageWithMetadata[P2]

}
