/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.serialization;

import static org.junit.Assert.assertEquals;

import akka.actor.ExtendedActorSystem;
import akka.serialization.Serialization;
import akka.serialization.Serializer;
import akka.serialization.SerializerWithStringManifest;
import akka.serialization.Serializers;
import akka.serialization.jackson.JacksonJsonSerializer;
import com.lightbend.lagom.internal.jackson.OldJacksonJsonSerializer;
import org.pcollections.TreePVector;

import org.pcollections.PVector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.NotSerializableException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.serialization.SerializationExtension;
import akka.testkit.javadsl.TestKit;

public class JacksonJsonSerializerTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    // @formatter:off
    Config conf =
        ConfigFactory.parseString(
            "akka.serialization.jackson.migrations {\n"
                + "  \"com.lightbend.lagom.serialization.Event1\" = \"com.lightbend.lagom.serialization.TestEventMigration\" \n"
                + "  \"com.lightbend.lagom.serialization.Event2\" = \"com.lightbend.lagom.serialization.TestEventMigration\" \n"
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

  private final OldJacksonJsonSerializer oldSerializer =
      new OldJacksonJsonSerializer((ExtendedActorSystem) system);
  private final int oldSerializerId = 1000002;

  static class CompatData {
    final int serializerId;
    final String manifest;
    final String json;
    final Object expected;
    final boolean expectIdenticalJson;

    CompatData(
        int serializerId,
        String manifest,
        String json,
        Object expected,
        boolean expectIdenticalJson) {
      this.serializerId = serializerId;
      this.manifest = manifest;
      this.json = q(json); // replacing ' with "
      this.expected = expected;
      this.expectIdenticalJson = expectIdenticalJson;
    }
  }

  private void checkSerialization(Object obj, boolean expectedCompression) {
    try {
      Serializer serializer = serializerFor(obj);
      String manifest = Serializers.manifestFor(serializer, obj);
      int serializerId = serializer.identifier();

      // check that it is configured to the Akka JacksonJsonSerializer
      assertEquals(
          "akka.serialization.jackson.JacksonJsonSerializer", serializer.getClass().getName());

      // verify serialization-deserialization round trip
      byte[] blob = serializer.toBinary(obj);

      if (!isGZipped(blob)) System.out.println(obj + " -> " + new String(blob, "utf-8"));

      assertEquals(expectedCompression, isGZipped(blob));
      Object obj2 = serialization.deserialize(blob, serializerId, manifest).get();
      assertEquals(obj, obj2);

    } catch (UnsupportedEncodingException | NotSerializableException e) {
      // that should not happen in testing,
      // but we need to make the compiler happy
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

  private <T> T deserialize(Class<T> clazz, String json) {
    try {
      byte[] bytes = json.getBytes("utf-8");
      return SerializationExtension.get(system).deserialize(bytes, clazz).get();
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String q(String s) {
    return s.replace("'", "\"");
  }

  @Test
  public void testSerializeImmutablesJsonableMessage() {
    SimpleCommand msg = SimpleCommand.of("Bob");
    assertEquals(true, msg instanceof Jsonable);
    checkSerialization(msg, false);
  }

  @Test
  public void testSmallCompressedJsonableMessage() {
    LargeCommand msg = LargeCommand.of("Some text.");
    assertEquals(true, msg instanceof CompressedJsonable);
    checkSerialization(msg, false);
  }

  @Test
  public void testBigCompressedJsonableMessage() {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < 32 * 1024; i++) {
      b.append("a");
    }
    LargeCommand msg = LargeCommand.of(b.toString());
    assertEquals(true, msg instanceof CompressedJsonable);
    checkSerialization(msg, true);
  }

  @Test
  public void testSerializeMessageWithoutDefaultConstructor() {
    checkSerialization(TestEntityMessages.Add.of("a"), false);
    checkSerialization(new TestEntityMessages.Appended("a"), false);
  }

  @Test
  public void testSerializeMessageEnumField() {
    checkSerialization(new TestEntityMessages.ChangeMode(TestEntityMessages.Mode.PREPEND), false);
  }

  @Test
  public void testSerializeMessageWithoutFields() {
    checkSerialization(new TestEntityMessages.UndefinedCmd(), false);
  }

  @Test
  public void testSerializeSingletonInstance() {
    checkSerialization(TestEntityMessages.Get.instance(), false);
    checkSerialization(TestEntityMessages.InPrependMode.instance(), false);
  }

  @Test
  public void testSerializeOptionalField() {
    OptionalCommand msg =
        OptionalCommand.builder().name("Bob").maybe(Optional.of("SomeOrg")).build();
    checkSerialization(msg, false);
  }

  @Test
  public void testSerializeUnsetOptionalField() {
    OptionalCommand msg = OptionalCommand.builder().name("Bob").build();
    checkSerialization(msg, false);
  }

  @Test
  public void testDeserializeWithoutOptionalField() {
    OptionalCommand msg = deserialize(OptionalCommand.class, q("{'name':'Bob'}"));
    assertEquals(OptionalCommand.of("Bob"), msg);
  }

  @Test
  public void testBooleanTrueField() {
    BooleanCommand msg = BooleanCommand.builder().isPublished(true).build();
    checkSerialization(msg, false);
  }

  @Test
  public void testBooleanFalseField() {
    BooleanCommand msg = BooleanCommand.builder().isPublished(false).build();
    checkSerialization(msg, false);
  }

  @Test
  public void testSerializeDateField() {
    Greeting msg = Greeting.of("Hello", LocalDateTime.now());
    checkSerialization(msg, false);
  }

  @Test
  public void testImmutablesWithCollection() {
    PVector<Comment> comments =
        TreePVector.<Comment>empty()
            .plus(Comment.of("user1", "+1"))
            .plus(Comment.of("user2", "-1"));
    PostContent content =
        PostContent.builder().title("Some Title").body("Content...").comments(comments).build();
    checkSerialization(content, false);
  }

  @Test
  public void testDeserializeWithMigration() throws NotSerializableException {
    Event1 event1 = Event1.of("a");
    SerializerWithStringManifest serializer = serializerFor(event1);
    byte[] blob = serializer.toBinary(event1);

    Event2 event2 = (Event2) serializer.fromBinary(blob, Event1.class.getName());
    assertEquals(event1.getField1(), event2.getField1V2());
    assertEquals(17, event2.getField2());
  }

  @Test
  public void testDeserializeWithMigrationFromV2() throws NotSerializableException {
    Event1 event1 = Event1.of("a");
    SerializerWithStringManifest serializer = serializerFor(event1);
    byte[] blob = serializer.toBinary(event1);

    Event2 event2 = (Event2) serializer.fromBinary(blob, Event1.class.getName() + "#2");
    assertEquals(event1.getField1(), event2.getField1V2());
    assertEquals(17, event2.getField2());
  }

  @Test
  public void testSerializeDone() {
    OptionalCommand msg =
        OptionalCommand.builder().name("Bob").maybe(Optional.of("SomeOrg")).build();
    checkSerialization(msg, false);
  }

  @Test
  public void testDeserializeOld() throws NotSerializableException, UnsupportedEncodingException {
    PVector<Comment> comments =
        TreePVector.<Comment>empty()
            .plus(Comment.of("user1", "+1"))
            .plus(Comment.of("user2", "-1"));
    PostContent content =
        PostContent.builder().title("Some Title").body("Content...").comments(comments).build();

    // These payloads and manifests were captured from Lagom 1.5 JacksonJsonSerializer
    List<CompatData> oldCompatData =
        Arrays.asList(
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.TestEntityMessages$Add",
                "{'element':'a','times':1}",
                TestEntityMessages.Add.of("a"),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.TestEntityMessages$Appended",
                "{'element':'a'}",
                new TestEntityMessages.Appended("a"),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.BooleanCommand",
                "{'isPublished':false}",
                BooleanCommand.builder().isPublished(false).build(),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.BooleanCommand",
                "{'isPublished':true}",
                BooleanCommand.builder().isPublished(true).build(),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.OptionalCommand",
                "{'name':'Bob','maybe':'SomeOrg'}",
                OptionalCommand.builder().name("Bob").maybe(Optional.of("SomeOrg")).build(),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.OptionalCommand",
                "{'name':'Bob','maybe':null}",
                OptionalCommand.builder().name("Bob").build(),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.OptionalCommand",
                "{'name':'Bob'}",
                OptionalCommand.builder().name("Bob").build(),
                false), // leaving out the optional maybe field
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.TestEntityMessages$ChangeMode",
                "{'mode':'PREPEND'}",
                new TestEntityMessages.ChangeMode(TestEntityMessages.Mode.PREPEND),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.TestEntityMessages$Get",
                "{}",
                TestEntityMessages.Get.instance(),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.TestEntityMessages$InPrependMode",
                "{}",
                TestEntityMessages.InPrependMode.instance(),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.PostContent",
                "{'title':'Some Title','body':'Content...','comments':[{'author':'user1','content':'+1'},{'author':'user2','content':'-1'}]}",
                content,
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.Greeting",
                "{'message':'Hello','timestamp':[2019,5,23,9,54,32,174000000]}",
                Greeting.of("Hello", LocalDateTime.of(2019, 5, 23, 9, 54, 32, 174000000)),
                false), // different date format
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.LargeCommand",
                "{'payload':'Some text.'}",
                LargeCommand.of("Some text."),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.TestEntityMessages$UndefinedCmd",
                "{}",
                new TestEntityMessages.UndefinedCmd(),
                true),
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.Event1#2",
                "{'field1':'a'}",
                Event2.of("a", 17),
                false), // migrations
            new CompatData(
                oldSerializerId,
                "com.lightbend.lagom.serialization.Event1",
                "{'field1':'a'}",
                Event2.of("a", 17),
                false) // migrations
            );

    for (CompatData compatData : oldCompatData) {
      // not strictly necessary that the JSON String representation is identical but can
      // be good to notice if it changes.
      if (compatData.expectIdenticalJson) {
        String newJson = new String(serialization.serialize(compatData.expected).get(), "utf-8");
        assertEquals(compatData.json, newJson);
      }

      Object result =
          serialization
              .deserialize(
                  compatData.json.getBytes("utf-8"), compatData.serializerId, compatData.manifest)
              .get();
      assertEquals(compatData.expected, result);
    }
  }
}
