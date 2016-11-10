package docs.home.serialization.v2c;

//#rename
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightbend.lagom.serialization.JacksonJsonMigration;

public class ItemAddedMigration extends JacksonJsonMigration {

  @Override
  public int currentVersion() {
    return 2;
  }

  @Override
  public JsonNode transform(int fromVersion, JsonNode json) {
    ObjectNode root = (ObjectNode) json;
    if (fromVersion <= 1) {
      root.set("itemId", root.get("productId"));
      root.remove("productId");
    }
    return root;
  }

}
//#rename
