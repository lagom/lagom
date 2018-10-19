package com.lightbend.hello.api;

import lombok.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@Value
@JsonDeserialize
public final class GreetingMessage {

  public final String message;

  @JsonCreator
  public GreetingMessage(String message) {
    this.message = Preconditions.checkNotNull(message, "message");
  }
}
