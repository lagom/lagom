package com.lightbend.hello.impl;

import lombok.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;

/**
 * The state for the {@link Hello} entity.
 */
@SuppressWarnings("serial")
@Value
@JsonDeserialize
public final class HelloState implements CompressedJsonable {

  public final String message;
  public final String timestamp;

  @JsonCreator
  public HelloState(String message, String timestamp) {
    this.message = Preconditions.checkNotNull(message, "message");
    this.timestamp = Preconditions.checkNotNull(timestamp, "timestamp");
  }
}
