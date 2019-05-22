/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.serialization;

import static org.junit.Assert.assertEquals;

import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import akka.serialization.Serializer;
import akka.serialization.SerializerWithStringManifest;
import akka.serialization.Serializers;
import docs.home.serialization.v1.OrderAdded;

import docs.home.serialization.v2d.OrderPlaced;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import docs.home.serialization.v2a.Customer;
import docs.home.serialization.v2a.ItemAdded;

import java.io.NotSerializableException;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.testkit.javadsl.TestKit;

public class EventMigrationTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    // @formatter:off
    Config conf =
        ConfigFactory.parseString(
            "akka.serialization.jackson.migrations {\n"
                + "  \"docs.home.serialization.v2b.ItemAdded\" = \"docs.home.serialization.v2b.ItemAddedMigration\" \n"
                + "  \"docs.home.serialization.v2c.ItemAdded\" = \"docs.home.serialization.v2c.ItemAddedMigration\" \n"
                + "  \"docs.home.serialization.v2a.Customer\" = \"docs.home.serialization.v2a.CustomerMigration\" \n"
                + "  \"docs.home.serialization.v1.OrderAdded\" = \"docs.home.serialization.v2d.OrderPlacedMigration\" \n"
                + "}\n");
    // @formatter:on
    system = ActorSystem.create("HelloWorldTest", conf);
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  private final Serialization serialization = SerializationExtension.get(system);

  private void checkSerialization(Object obj, boolean expectedCompression) {
    try {
      // verify serialization-deserialization round trip
      Serializer serializer = serialization.serializerFor(obj.getClass());
      String manifest = Serializers.manifestFor(serializer, obj);
      int serializerId = serializer.identifier();

      byte[] blob = serializer.toBinary(obj);
      assertEquals(expectedCompression, isGZipped(blob));

      Object obj2 = serialization.deserialize(blob, serializerId, manifest).get();
      assertEquals(obj, obj2);
    } catch (NotSerializableException e) {
      throw new RuntimeException(e);
    }
  }

  private SerializerWithStringManifest serializerFor(Object obj) throws NotSerializableException {
    return (SerializerWithStringManifest) serialization.serializerFor(obj.getClass());
  }

  private boolean isGZipped(byte[] bytes) {
    return (bytes != null)
        && (bytes.length >= 2)
        && (bytes[0] == (byte) GZIPInputStream.GZIP_MAGIC)
        && (bytes[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
  }

  @Test
  public void testAddOptionalField() throws NotSerializableException {
    docs.home.serialization.v1.ItemAdded event1 =
        docs.home.serialization.v1.ItemAdded.builder()
            .shoppingCartId("123")
            .productId("ab123")
            .quantity(2)
            .build();
    SerializerWithStringManifest serializer = serializerFor(event1);
    byte[] blob = serializer.toBinary(event1);
    ItemAdded event2 = (ItemAdded) serializer.fromBinary(blob, ItemAdded.class.getName());
    assertEquals(event1.getQuantity(), event2.getQuantity());
    assertEquals(Optional.empty(), event2.getDiscount());
    assertEquals("", event2.getNote());

    checkSerialization(
        ItemAdded.builder()
            .shoppingCartId("123")
            .productId("ab123")
            .quantity(2)
            .discount(0.1)
            .note("thanks")
            .build(),
        false);
  }

  @Test
  public void testAddMandatoryField() throws NotSerializableException {
    docs.home.serialization.v1.ItemAdded event1 =
        docs.home.serialization.v1.ItemAdded.builder()
            .shoppingCartId("123")
            .productId("ab123")
            .quantity(2)
            .build();
    SerializerWithStringManifest serializer = serializerFor(event1);
    byte[] blob = serializer.toBinary(event1);
    docs.home.serialization.v2b.ItemAdded event2 =
        (docs.home.serialization.v2b.ItemAdded)
            serializer.fromBinary(blob, docs.home.serialization.v2b.ItemAdded.class.getName());
    assertEquals(event1.getQuantity(), event2.getQuantity());
    assertEquals(0.0, event2.getDiscount(), 0.000001);

    checkSerialization(
        docs.home.serialization.v2b.ItemAdded.builder()
            .shoppingCartId("123")
            .productId("ab123")
            .quantity(2)
            .discount(0.1)
            .build(),
        false);
  }

  @Test
  public void testRenameField() throws NotSerializableException {
    docs.home.serialization.v1.ItemAdded event1 =
        docs.home.serialization.v1.ItemAdded.builder()
            .shoppingCartId("123")
            .productId("ab123")
            .quantity(2)
            .build();
    SerializerWithStringManifest serializer = serializerFor(event1);
    byte[] blob = serializer.toBinary(event1);
    docs.home.serialization.v2c.ItemAdded event2 =
        (docs.home.serialization.v2c.ItemAdded)
            serializer.fromBinary(blob, docs.home.serialization.v2c.ItemAdded.class.getName());
    assertEquals(event1.getProductId(), event2.getItemId());
    assertEquals(event1.getQuantity(), event2.getQuantity());
  }

  @Test
  public void testStructuralChanges() throws NotSerializableException {
    docs.home.serialization.v1.Customer cust1 =
        docs.home.serialization.v1.Customer.builder()
            .name("A")
            .street("B")
            .city("C")
            .zipCode("D")
            .country("E")
            .build();
    SerializerWithStringManifest serializer = serializerFor(cust1);
    byte[] blob = serializer.toBinary(cust1);
    Customer cust2 = (Customer) serializer.fromBinary(blob, Customer.class.getName());
    assertEquals(cust1.getName(), cust2.getName());
    assertEquals(cust1.getStreet(), cust2.getShippingAddress().getStreet());
    assertEquals(cust1.getCity(), cust2.getShippingAddress().getCity());
    assertEquals(cust1.getZipCode(), cust2.getShippingAddress().getZipCode());
    assertEquals(cust1.getCountry(), cust2.getShippingAddress().getCountry());
  }

  @Test
  public void testRenameClass() throws NotSerializableException {
    OrderAdded order1 = OrderAdded.builder().shoppingCartId("1234").build();
    SerializerWithStringManifest serializer = serializerFor(order1);
    byte[] blob = serializer.toBinary(order1);
    OrderPlaced order2 = (OrderPlaced) serializer.fromBinary(blob, OrderAdded.class.getName());
    assertEquals(order1.getShoppingCartId(), order2.getShoppingCartId());
  }
}
