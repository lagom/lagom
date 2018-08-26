/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;
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

    private class Dummy {
        private final Optional<String> opt;

        Dummy(Optional<String> opt) {
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
    public void testDeserializeEmptyByteStringToOptional() {
        StrictMessageSerializer<Optional<String>> serializer = factory.messageSerializerFor(Optional.class);
        Optional<String> out = serializer.deserializer(new MessageProtocol()).deserialize(ByteString.empty());
        assertEquals(out, Optional.empty());
    }

    @Test(expected = DeserializationException.class)
    public void testFailToDeserializeEmptyByteStringToDummy() {
        StrictMessageSerializer<Dummy> serializer = factory.messageSerializerFor(Dummy.class);
        serializer.deserializer(new MessageProtocol()).deserialize(ByteString.empty());
    }
}
