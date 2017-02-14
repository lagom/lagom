/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

public interface Command extends Jsonable {

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = SimpleCommand.class)
  public interface AbstractSimpleCommand extends Command {
    @Value.Parameter
    String getName();
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = LargeCommand.class)
  public interface AbstractLargeCommand extends Command, CompressedJsonable {
    @Value.Parameter
    String getPayload();
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = OptionalCommand.class)
  public interface AbstractOptionalCommand extends Command {
    @Value.Parameter
    String getName();

    Optional<String> getMaybe();
  }

  @Value.Immutable
  @ImmutableStyle
  @JsonDeserialize(as = BooleanCommand.class)
  public interface AbstractBooleanCommand extends Command {
    @Value.Parameter
    @JsonProperty(value = "isPublished")
    boolean isPublished();
  }

}
