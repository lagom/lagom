/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.helloworld.impl;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

public class HelloEventTag {

  public static final AggregateEventTag<HelloEvent> INSTANCE =
    AggregateEventTag.of(HelloEvent.class);

}
