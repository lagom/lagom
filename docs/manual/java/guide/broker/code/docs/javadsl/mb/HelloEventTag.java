/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

public class HelloEventTag {

  public static final AggregateEventTag<HelloEvent> INSTANCE =
      AggregateEventTag.of(HelloEvent.class);
}
