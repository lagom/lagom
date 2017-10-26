/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.util.Optional;

/**
 * A message broker message.
 *
 * This provides access to both the message payload, and the metadata.
 */
public final class Message<Payload> {

  private final Payload payload;
  private final PMap<MetadataKey<?>, Object> metadataMap;

  private Message(Payload payload, PMap<MetadataKey<?>, Object> metadataMap) {
    this.payload = payload;
    this.metadataMap = metadataMap;
  }

  /**
   * The payload of the message.
   */
  public Payload getPayload() {
    return payload;
  }

  /**
   * Get the metadata for the given metadata key.
   *
   * @param key The key of the metadata.
   * @return The metadata, if found for that key.
   */
  @SuppressWarnings("unchecked")
  public <Metadata> Optional<Metadata> get(MetadataKey<Metadata> key) {
    return Optional.ofNullable((Metadata) metadataMap.get(key));
  }

  /**
   * Add a metadata key and value to this message.
   *
   * @param key The key to add.
   * @param metadata The metadata to add.
   * @return A copy of this message with the key and value added.
   */
  public <Metadata> Message<Payload> add(MetadataKey<Metadata> key, Metadata metadata) {
    return new Message<>(payload, metadataMap.plus(key, metadata));
  }

  /**
   * Return a copy of this message with the given payload.
   *
   * @param payload The payload.
   * @return A copy of this message with the given payload.
   */
  public <P2> Message<P2> withPayload(P2 payload) {
    return new Message<>(payload, metadataMap);
  }

  /**
   * Get the message key as a String.
   * <p>
   * If the key is not a String, it will be converted to a String using <code>toString</code>.
   * <p>
   * If the underlying message broker doesn't support keys, the empty string will be returned.
   *
   * @return A string representation of the message key.
   */
  public String messageKeyAsString() {
    return get(MetadataKey.messageKey()).map(Object::toString).orElse("");
  }

  /**
   * Create a message with the given payload.
   */
  public static <Payload> Message<Payload> create(Payload payload) {
    return new Message<>(payload, HashTreePMap.empty());
  }

}
