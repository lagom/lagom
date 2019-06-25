/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.serialization.v2d;

import com.fasterxml.jackson.databind.JsonNode;
import akka.serialization.jackson.JacksonMigration;
// #rename-class

public class OrderPlacedMigration extends JacksonMigration {

  @Override
  public int currentVersion() {
    return 2;
  }

  @Override
  public String transformClassName(int fromVersion, String className) {
    return OrderPlaced.class.getName();
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    return json;
  }
}
// #rename-class
