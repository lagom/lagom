/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.helloworld.impl;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.Jsonable;

/**
 * This interface defines all the events that the HelloWorld entity supports.
 * <p>
 * By convention, the events should be inner classes of the interface, which
 * makes it simple to get a complete picture of what events an entity has.
 */
public interface HelloEvent extends Jsonable {

  /**
   * An event that represents a change in greeting message.
   */
  @SuppressWarnings("serial")
  @Immutable
  @JsonDeserialize
  public final class GreetingMessageChanged implements HelloEvent {
    public final String message;

    @JsonCreator
    public GreetingMessageChanged(String message) {
      this.message = Preconditions.checkNotNull(message, "message");
    }

    @Override
    public boolean equals(@Nullable Object another) {
      if (this == another)
        return true;
      return another instanceof GreetingMessageChanged && equalTo((GreetingMessageChanged) another);
    }

    private boolean equalTo(GreetingMessageChanged another) {
      return message.equals(another.message);
    }

    @Override
    public int hashCode() {
      int h = 31;
      h = h * 17 + message.hashCode();
      return h;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper("GreetingMessageChanged").add("message", message).toString();
    }
  }
}
