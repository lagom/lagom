/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.pcollections.TreePVector;

import org.pcollections.PVector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.lightbend.lagom.internal.jackson.JacksonJsonSerializer;

import java.io.NotSerializableException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.serialization.SerializationExtension;
import akka.testkit.JavaTestKit;

public class JacksonJsonSerializerTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    // @formatter:off
    Config conf = ConfigFactory.parseString(
      "lagom.serialization.json.migrations {\n" +
      "  \"com.lightbend.lagom.serialization.Event1\" = \"com.lightbend.lagom.serialization.TestEventMigration\" \n" +
      "  \"com.lightbend.lagom.serialization.Event2\" = \"com.lightbend.lagom.serialization.TestEventMigration\" \n" +
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
    try {

        // check that it is configured
        assertEquals(JacksonJsonSerializer.class,
            SerializationExtension.get(system).serializerFor(obj.getClass()).getClass()
        );

        // verify serialization-deserialization round trip
        byte[] blob = serializer.toBinary(obj);


        if (!serializer.isGZipped(blob))
            System.out.println(obj + " -> " + new String(blob, "utf-8"));

        assertEquals(expectedCompression, serializer.isGZipped(blob));
        Object obj2 = serializer.fromBinary(blob, serializer.manifest(obj));
        assertEquals(obj, obj2);

    } catch (UnsupportedEncodingException | NotSerializableException e) {
        // that should not happen in testing,
        // but we need to make the compiler happy
        throw new RuntimeException(e);
    }
  }

  private <T> T deserialize(Class<T> clazz, String json) {
    try {
      byte[] bytes = json.getBytes("utf-8");
      return SerializationExtension.get(system).deserialize(bytes, clazz).get();
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private String q(String s) {
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
    for (int i = 0; i < 1024; i++) {
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
    OptionalCommand msg = OptionalCommand.builder().name("Bob").maybe(Optional.of("SomeOrg")).build();
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
    PVector<Comment> comments = TreePVector.<Comment>empty().plus(Comment.of("user1", "+1"))
        .plus(Comment.of("user2", "-1"));
    PostContent content = PostContent.builder().title("Some Title").body("Content...").comments(comments).build();
    checkSerialization(content, false);
  }


  @Test
  public void testDeserializeWithMigration() {
    Event1 event1 = Event1.of("a");
    byte[] blob = serializer.toBinary(event1);

    Event2 event2 = (Event2) serializer.fromBinary(blob, Event1.class.getName());
    assertEquals(event1.getField1(), event2.getField1V2());
    assertEquals(17, event2.getField2());
  }
  
  @Test
  public void testDeserializeWithMigrationFromV2() {
    Event1 event1 = Event1.of("a");
    byte[] blob = serializer.toBinary(event1);

    Event2 event2 = (Event2) serializer.fromBinary(blob, Event1.class.getName() + "#2");
    assertEquals(event1.getField1(), event2.getField1V2());
    assertEquals(17, event2.getField2());
  }

  @Test
  public void testSerializeDone() {
    OptionalCommand msg = OptionalCommand.builder().name("Bob").maybe(Optional.of("SomeOrg")).build();
    checkSerialization(msg, false);
  }




}
