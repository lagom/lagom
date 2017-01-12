/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package sample.helloworld.impl;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

public class HelloEventTag {

  public static final AggregateEventTag<HelloEvent> INSTANCE =
    AggregateEventTag.of(HelloEvent.class);

}
