/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.broker;

import com.lightbend.lagom.internal.api.broker.MessageMetadataKey;
import com.lightbend.lagom.internal.api.broker.MessageWithMetadata;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import scala.Option;

import java.util.Optional;

/**
 * A message broker message.
 *
 * <p>This provides access to both the message payload, and the metadata.
 */
public final class Message<Payload> implements MessageWithMetadata<Payload> {

  private final Payload payload;
  private final PMap<MessageMetadataKey<?>, Object> metadataMap;

  private Message(Payload payload, PMap<MessageMetadataKey<?>, Object> metadataMap) {
    this.payload = payload;
    this.metadataMap = metadataMap;
  }

  /** The payload of the message. */
  public Payload getPayload() {
    return payload;
  }

  @Override
  public Payload payload() {
    return getPayload();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Metadata> Option<Metadata> getMetadata(MessageMetadataKey<Metadata> key) {
    return Option.apply((Metadata) metadataMap.get(key));
  }

  /**
   * Get the metadata for the given metadata key.
   *
   * @param key The key of the metadata.
   * @return The metadata, if found for that key.
   */
  @SuppressWarnings("unchecked")
  public <Metadata> Optional<Metadata> get(MessageMetadataKey<Metadata> key) {
    return Optional.ofNullable((Metadata) metadataMap.get(key));
  }

  /**
   * Get the metadata for the given metadata key.
   *
   * @param key The key of the metadata.
   * @return The metadata, if found for that key.
   */
  @SuppressWarnings("unchecked")
  public <Metadata> Optional<Metadata> get(MetadataKey<Metadata> key) {
    return Optional.ofNullable((Metadata) metadataMap.get(key.asMessageMetadataKey()));
  }

  /**
   * Add a metadata key and value to this message.
   *
   * @param key The key to add.
   * @param metadata The metadata to add.
   * @return A copy of this message with the key and value added.
   */
  public <Metadata> Message<Payload> add(MessageMetadataKey<Metadata> key, Metadata metadata) {
    return new Message<>(payload, metadataMap.plus(key, metadata));
  }

  /**
   * Add a metadata key and value to this message.
   *
   * @param key The key to add.
   * @param metadata The metadata to add.
   * @return A copy of this message with the key and value added.
   */
  public <Metadata> Message<Payload> add(MetadataKey<Metadata> key, Metadata metadata) {
    return new Message<>(payload, metadataMap.plus(key.asMessageMetadataKey(), metadata));
  }

  /**
   * Return a copy of this message with the given payload.
   *
   * @param payload The payload.
   * @return A copy of this message with the given payload.
   */
  @Override
  public <P2> Message<P2> withPayload(P2 payload) {
    return new Message<>(payload, metadataMap);
  }

  /**
   * Get the message key as a String.
   *
   * <p>If the key is not a String, it will be converted to a String using <code>toString</code>.
   *
   * <p>If the underlying message broker doesn't support keys, the empty string will be returned.
   *
   * @return A string representation of the message key.
   */
  public String messageKeyAsString() {
    return get(MessageMetadataKey.messageKey()).map(Object::toString).orElse("");
  }

  /** Create a message with the given payload. */
  public static <Payload> Message<Payload> create(Payload payload) {
    return new Message<>(payload, HashTreePMap.empty());
  }
}
