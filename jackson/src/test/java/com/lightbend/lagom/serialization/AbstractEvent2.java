/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;
import org.immutables.value.Value.Parameter;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = Event2.class)
public interface AbstractEvent2 extends Jsonable {

  @Parameter
  String getField1V2(); // renamed from field1

  @Parameter
  int getField2(); // new mandatory field

}
