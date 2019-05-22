/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.serialization;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;
import akka.util.ByteString$;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lightbend.lagom.javadsl.api.deser.DeserializationException;
import com.lightbend.lagom.javadsl.api.deser.StrictMessageSerializer;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.jackson.JacksonSerializerFactory;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class JacksonSerializerFactoryTest {

  static ActorSystem system;

  static class Dummy {
    private final Optional<String> opt;

    @JsonCreator
    Dummy(@JsonProperty("opt") Optional<String> opt) {
      this.opt = opt;
    }
  }

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("HelloWorldTest", ConfigFactory.defaultApplication());
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  private final JacksonSerializerFactory factory = new JacksonSerializerFactory(system);

  @Test
  public void shouldDeserializeEmptyByteStringToOptionalEmpty() {
    StrictMessageSerializer<Optional<String>> serializer =
        factory.messageSerializerFor(Optional.class);
    Optional<String> out =
        serializer.deserializer(new MessageProtocol()).deserialize(ByteString$.MODULE$.empty());
    assertEquals(Optional.empty(), out);
  }

  @Test(expected = DeserializationException.class)
  public void shouldFailToDeserializeEmptyByteStringToDummy() {
    StrictMessageSerializer<Dummy> serializer = factory.messageSerializerFor(Dummy.class);
    serializer.deserializer(new MessageProtocol()).deserialize(ByteString.empty());
  }

  @Test
  public void shouldDeserializeAValidEmptyJsObjectToDummy() {
    StrictMessageSerializer<Dummy> serializer = factory.messageSerializerFor(Dummy.class);
    Dummy deserialize =
        serializer.deserializer(new MessageProtocol()).deserialize(ByteString.fromString("{}"));
    assertEquals(Optional.empty(), deserialize.opt);
  }

  @Test
  public void shouldDeserializeAValidNullifiedJsObjectToDummy() {
    StrictMessageSerializer<Dummy> serializer = factory.messageSerializerFor(Dummy.class);
    Dummy deserialize =
        serializer
            .deserializer(new MessageProtocol())
            .deserialize(ByteString.fromString("{\"opt\":null}"));
    assertEquals(Optional.empty(), deserialize.opt);
  }

  @Test
  public void shouldDeserializeAValidJsObjectToDummy() {
    StrictMessageSerializer<Dummy> serializer = factory.messageSerializerFor(Dummy.class);
    Dummy deserialize =
        serializer
            .deserializer(new MessageProtocol())
            .deserialize(ByteString.fromString("{\"opt\":\"abc\"}"));
    assertEquals(Optional.of("abc"), deserialize.opt);
  }

  @Test
  public void shouldDeserializeByteStringToByteString() {
    StrictMessageSerializer<ByteString> serializer = factory.messageSerializerFor(ByteString.class);
    ByteString byteString = ByteString.fromString("{\"opt\":\"abc\"}");
    ByteString deserialize = serializer.deserializer(new MessageProtocol()).deserialize(byteString);
    assertEquals(byteString, deserialize);
  }

  @Test
  public void shouldSerializeByteStringToByteString() {
    StrictMessageSerializer<ByteString> serializer = factory.messageSerializerFor(ByteString.class);
    ByteString byteString = ByteString.fromString("{\"opt\":\"abc\"}");
    ByteString deserialize = serializer.serializerForRequest().serialize(byteString);
    assertEquals(byteString, deserialize);
  }
}
