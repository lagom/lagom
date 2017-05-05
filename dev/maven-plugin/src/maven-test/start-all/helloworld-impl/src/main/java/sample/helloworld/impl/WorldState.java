/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package sample.helloworld.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;

/**
 * The state for the {@link HelloWorld} entity.
 */
@SuppressWarnings("serial")
@JsonDeserialize
public final class WorldState implements CompressedJsonable {

  public final String message;
  public final String timestamp;

  @JsonCreator
  public WorldState(String message, String timestamp) {
    this.message = Preconditions.checkNotNull(message, "message");
    this.timestamp = Preconditions.checkNotNull(timestamp, "timestamp");
  }

  @Override
  public boolean equals(Object another) {
    return this == another || another instanceof WorldState && equalTo((WorldState) another);
  }

  private boolean equalTo(WorldState another) {
    return message.equals(another.message) && timestamp.equals(another.timestamp);
  }

  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + message.hashCode();
    h = h * 17 + timestamp.hashCode();
    return h;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("WorldState").add("message", message).add("timestamp", timestamp).toString();
  }
}
