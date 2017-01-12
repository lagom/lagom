/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service1Name}.impl;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;

/**
 * The state for the {@link ${service1ClassName}} entity.
 */
@SuppressWarnings("serial")
@Immutable
@JsonDeserialize
public final class ${service1ClassName}State implements CompressedJsonable {

  public final String message;
  public final String timestamp;

  @JsonCreator
  public ${service1ClassName}State(String message, String timestamp) {
    this.message = Preconditions.checkNotNull(message, "message");
    this.timestamp = Preconditions.checkNotNull(timestamp, "timestamp");
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another)
      return true;
    return another instanceof ${service1ClassName}State && equalTo((${service1ClassName}State) another);
  }

  private boolean equalTo(${service1ClassName}State another) {
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
    return MoreObjects.toStringHelper("${service1ClassName}State").add("message", message).add("timestamp", timestamp).toString();
  }
}
