/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import com.lightbend.lagom.serialization.Jsonable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = Comment.class)
public interface AbstractComment extends Jsonable {
  @Value.Parameter
  String getAuthor();

  @Value.Parameter
  String getContent();

}
