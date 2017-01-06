/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = Greeting.class)
public abstract class AbstractGreeting implements CompressedJsonable {
  @Value.Parameter
  public abstract String getMessage();

  @Value.Parameter
  public abstract LocalDateTime getTimestamp();
}
