/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import com.lightbend.lagom.serialization.Jsonable;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

public interface HelloEvent extends Jsonable, AggregateEvent<HelloEvent> {

  @Override
  public default AggregateEventTag<HelloEvent> aggregateTag() {
    return HelloEventTag.INSTANCE;
  }

  String getId();

  String getMessage();

  final class AbstractGreetingMessageChanged implements HelloEvent {
    private final String id;
    private final String message;

    public AbstractGreetingMessageChanged(String id, String message) {
      this.id = id;
      this.message = message;
    }

    @Override
    public String getId() {
      return id;
    }

    public String getMessage() {
      return message;
    }
  }
}
