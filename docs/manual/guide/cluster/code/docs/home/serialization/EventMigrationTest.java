package docs.home.serialization;

import static org.junit.Assert.assertEquals;

import docs.home.serialization.v1.OrderAdded;

import docs.home.serialization.v2d.OrderPlaced;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.lightbend.lagom.internal.jackson.JacksonJsonSerializer;
import docs.home.serialization.v2a.Customer;
import docs.home.serialization.v2a.ItemAdded;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.testkit.JavaTestKit;

public class EventMigrationTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    // @formatter:off
    Config conf = ConfigFactory.parseString(
      "lagom.serialization.json.migrations {\n" +
      "  \"docs.home.serialization.v2b.ItemAdded\" = \"docs.home.serialization.v2b.ItemAddedMigration\" \n" +
      "  \"docs.home.serialization.v2c.ItemAdded\" = \"docs.home.serialization.v2c.ItemAddedMigration\" \n" +
      "  \"docs.home.serialization.v2a.Customer\" = \"docs.home.serialization.v2a.CustomerMigration\" \n" +
      "  \"docs.home.serialization.v1.OrderAdded\" = \"docs.home.serialization.v2d.OrderPlacedMigration\" \n" +
      "}\n");
    // @formatter:on
    system = ActorSystem.create("HelloWorldTest", conf);
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  private final JacksonJsonSerializer serializer = new JacksonJsonSerializer((ExtendedActorSystem) system);

  private void checkSerialization(Object obj, boolean expectedCompression) {
    // verify serialization-deserialization round trip
    byte[] blob = serializer.toBinary(obj);
    assertEquals(expectedCompression, serializer.isGZipped(blob));
    Object obj2 = serializer.fromBinary(blob, serializer.manifest(obj));
    assertEquals(obj, obj2);
  }

  @Test
  public void testAddOptionalField() {
    docs.home.serialization.v1.ItemAdded event1 =
        docs.home.serialization.v1.ItemAdded.builder().shoppingCartId("123").productId("ab123").quantity(2).build();
    byte[] blob = serializer.toBinary(event1);
    ItemAdded event2 =
     (ItemAdded) serializer.fromBinary(blob, ItemAdded.class.getName());
    assertEquals(event1.getQuantity(), event2.getQuantity());
    assertEquals(Optional.empty(), event2.getDiscount());
    assertEquals("", event2.getNote());

    checkSerialization(
      ItemAdded.builder().shoppingCartId("123").productId("ab123").quantity(2).discount(0.1).note("thanks").build(),
      false);
  }

  @Test
  public void testAddMandatoryField() {
    docs.home.serialization.v1.ItemAdded event1 =
        docs.home.serialization.v1.ItemAdded.builder().shoppingCartId("123").productId("ab123").quantity(2).build();
    byte[] blob = serializer.toBinary(event1);
    docs.home.serialization.v2b.ItemAdded event2 =
     (docs.home.serialization.v2b.ItemAdded) serializer.fromBinary(blob, docs.home.serialization.v2b.ItemAdded.class.getName());
    assertEquals(event1.getQuantity(), event2.getQuantity());
    assertEquals(0.0, event2.getDiscount(), 0.000001);

    checkSerialization(
        docs.home.serialization.v2b.ItemAdded.builder().shoppingCartId("123").productId("ab123")
          .quantity(2).discount(0.1).build(),
      false);
  }

  @Test
  public void testRenameField() {
    docs.home.serialization.v1.ItemAdded event1 =
        docs.home.serialization.v1.ItemAdded.builder().shoppingCartId("123").productId("ab123").quantity(2).build();
    byte[] blob = serializer.toBinary(event1);
    docs.home.serialization.v2c.ItemAdded event2 =
     (docs.home.serialization.v2c.ItemAdded) serializer.fromBinary(blob, docs.home.serialization.v2c.ItemAdded.class.getName());
    assertEquals(event1.getProductId(), event2.getItemId());
    assertEquals(event1.getQuantity(), event2.getQuantity());
  }

  @Test
  public void testStructuralChanges() {
    docs.home.serialization.v1.Customer cust1 =
        docs.home.serialization.v1.Customer.builder().name("A").street("B").city("C").zipCode("D").country("E").build();
    byte[] blob = serializer.toBinary(cust1);
    Customer cust2 =
     (Customer) serializer.fromBinary(blob, Customer.class.getName());
    assertEquals(cust1.getName(), cust2.getName());
    assertEquals(cust1.getStreet(), cust2.getShippingAddress().getStreet());
    assertEquals(cust1.getCity(), cust2.getShippingAddress().getCity());
    assertEquals(cust1.getZipCode(), cust2.getShippingAddress().getZipCode());
    assertEquals(cust1.getCountry(), cust2.getShippingAddress().getCountry());
  }

  @Test
  public void testRenameClass() {
    OrderAdded order1 = OrderAdded.builder().shoppingCartId("1234").build();
    byte[] blob = serializer.toBinary(order1);
    OrderPlaced order2 =
     (OrderPlaced) serializer.fromBinary(blob, OrderAdded.class.getName());
    assertEquals(order1.getShoppingCartId(), order2.getShoppingCartId());
  }






}
