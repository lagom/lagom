package docs.home.serialization.v2d;

import com.fasterxml.jackson.databind.JsonNode;
import com.lightbend.lagom.serialization.JacksonJsonMigration;
//#rename-class

public class OrderPlacedMigration extends JacksonJsonMigration {

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
//#rename-class
