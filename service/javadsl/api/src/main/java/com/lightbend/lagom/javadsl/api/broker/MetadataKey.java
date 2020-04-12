/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.broker;

import com.lightbend.lagom.internal.api.broker.MessageMetadataKey;

/**
 * A metadata key.
 *
 * @deprecated Use {@link MessageMetadataKey} instead.
 */
public final class MetadataKey<Metadata> {
  private final String name;

  private MetadataKey(String name) {
    this.name = name;
  }

  /** The name of the metadata key. */
  public String getName() {
    return name;
  }

  /** Transforms this key to {@link MessageMetadataKey}. * */
  public MessageMetadataKey<Metadata> asMessageMetadataKey() {
    return MessageMetadataKey.named(name);
  }

  /** Create a metadata key with the given name. */
  public static <Metadata> MetadataKey<Metadata> named(String name) {
    return new MetadataKey<>(name);
  }

  /** The message key metadata key. */
  @SuppressWarnings("unchecked")
  public static <Key> MetadataKey<Key> messageKey() {
    return (MetadataKey) MESSAGE_KEY;
  }

  private static MetadataKey<Object> MESSAGE_KEY = named("messageKey");

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MetadataKey<?> that = (MetadataKey<?>) o;

    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "MetadataKey{" + "name='" + name + '\'' + '}';
  }
}
