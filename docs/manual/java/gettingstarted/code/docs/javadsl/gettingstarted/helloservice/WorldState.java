/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.gettingstarted.helloservice;

public final class WorldState {
  public final String message;
  public final String timestamp;

  public WorldState(String message, String timestamp) {
    this.message = message;
    this.timestamp = timestamp;
  }
}
