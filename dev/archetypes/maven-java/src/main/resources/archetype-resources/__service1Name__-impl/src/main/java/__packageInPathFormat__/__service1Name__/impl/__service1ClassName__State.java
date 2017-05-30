/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service1Name}.impl;

import lombok.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;

/**
 * The state for the {@link ${service1ClassName}} entity.
 */
@SuppressWarnings("serial")
@Value
@JsonDeserialize
public final class ${service1ClassName}State implements CompressedJsonable {

  public final String message;
  public final String timestamp;

  @JsonCreator
  public ${service1ClassName}State(String message, String timestamp) {
    this.message = Preconditions.checkNotNull(message, "message");
    this.timestamp = Preconditions.checkNotNull(timestamp, "timestamp");
  }
}
