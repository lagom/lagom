/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.registry;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.util.ByteString;
import com.lightbend.lagom.internal.jackson.JacksonJsonSerializer;
import com.lightbend.lagom.javadsl.api.ServiceAcl;
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer;
import com.lightbend.lagom.javadsl.api.deser.StrictMessageSerializer;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.jackson.JacksonSerializerFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import akka.testkit.javadsl.TestKit;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 *
 */
public class ServiceRegistryServiceSerializationTest {

    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        // @formatter:off
        Config conf = ConfigFactory.parseString("");
        // @formatter:on
        system = ActorSystem.create("SerializationTest", conf);
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void serializationRoundTrip() throws URISyntaxException {
        List<ServiceAcl> acls = Arrays.asList(ServiceAcl.methodAndPath(Method.GET, "/.*"));
        ServiceRegistryService x = ServiceRegistryService.of(new URI("http://localhost/asdf"), acls);

        MessageProtocol protocol = new MessageProtocol(Optional.of("application/json"), Optional.empty(),
            Optional.empty());
        JacksonSerializerFactory factory = new JacksonSerializerFactory(system);
        StrictMessageSerializer<ServiceRegistryService> serializer =
            factory.messageSerializerFor(x.getClass());
        MessageSerializer.NegotiatedSerializer<ServiceRegistryService, ByteString> serializerForRequest =
            serializer.serializerForRequest();
        MessageSerializer.NegotiatedDeserializer<ServiceRegistryService, ByteString> deserializer =
            serializer.deserializer(protocol);

        Assert.assertEquals(x, deserializer.deserialize(serializerForRequest.serialize(x)));
    }
}
