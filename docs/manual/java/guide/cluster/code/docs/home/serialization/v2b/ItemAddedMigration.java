/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.serialization.v2b;

// #add
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import akka.serialization.jackson.JacksonMigration;

public class ItemAddedMigration extends JacksonMigration {

  @Override
  public int currentVersion() {
    return 2;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    ObjectNode root = (ObjectNode) json;
    if (fromVersion <= 1) {
      root.set("discount", DoubleNode.valueOf(0.0));
    }
    return root;
  }
}
// #add
