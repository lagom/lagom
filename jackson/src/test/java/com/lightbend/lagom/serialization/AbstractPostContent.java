/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import org.pcollections.TreePVector;

import org.pcollections.PVector;
import com.lightbend.lagom.serialization.Jsonable;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = PostContent.class)
public interface AbstractPostContent extends Jsonable {
  @Value.Parameter
  String getTitle();

  @Value.Parameter
  String getBody();

  @Value.Default
  default PVector<Comment> getComments() {
    return TreePVector.empty();
  }


}
