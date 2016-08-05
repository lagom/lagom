package sample.helloworld.api;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@Immutable
@JsonDeserialize
public final class GreetingMessage {

  public final String message;

  @JsonCreator
  public GreetingMessage(String message) {
    this.message = Preconditions.checkNotNull(message, "message");
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another)
      return true;
    return another instanceof GreetingMessage && equalTo((GreetingMessage) another);
  }

  private boolean equalTo(GreetingMessage another) {
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
    return MoreObjects.toStringHelper("GreetingMessage").add("message", message).toString();
  }
}
