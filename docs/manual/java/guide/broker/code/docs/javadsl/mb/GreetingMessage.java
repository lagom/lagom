/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import com.fasterxml.jackson.annotation.JsonCreator;

public final class GreetingMessage {
  private final String id;
  private final String message;

  @JsonCreator
  public GreetingMessage(String id, String message) {
    this.id = id;
    this.message = message;
  }

  public String getId() {
    return id;
  }

  public String getMessage() {
    return message;
  }
}
