/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it.mocks;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.lightbend.lagom.javadsl.testkit.ServiceTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.mvc.Http;
import play.mvc.Result;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer;
import static org.junit.Assert.assertEquals;
import play.test.*;


import static play.test.Helpers.*;

public class AdditionalRoutersServiceTest {

    private static ServiceTest.TestServer server;
    private static Materializer materializer;

    @BeforeClass
    public static void setUp() {
        server = startServer(defaultSetup()
            .withCluster(false).withCassandra(false)
            .configureBuilder(b -> b.bindings(new AdditionalRoutersServiceModule()))
        );
        materializer = server.materializer();
    }

    @AfterClass
    public static void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    public void testAdditionalPingRouter() throws Exception {
        // call the ping router (instance + bind dsl prefix)
        Http.RequestBuilder request = Helpers.fakeRequest(GET, "/ping/");
        Result result = route(server.app(), request);
        assertEquals(OK, result.status());
        assertEquals(result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "ping");
    }

    @Test
    public void testAdditionalPongRouter() throws Exception {
        // call the pong router (prefixed instance)
        Http.RequestBuilder request = Helpers.fakeRequest(GET, "/pong/");
        Result result = route(server.app(), request);
        assertEquals(OK, result.status());
        assertEquals(result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "pong");
    }

    @Test
    public void testAdditionalHelloRouter() throws Exception {
        // call the echo router (router instantiated using Injector + bind dsl prefix)
        Http.RequestBuilder request = Helpers.fakeRequest(GET, "/hello/");
        Result result = route(server.app(), request);
        assertEquals(OK, result.status());
        assertEquals(result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "Hello");
    }

    @Test
    public void testAdditionalPrefixedHelloRouter() throws Exception {
        // call the echo router (router instantiated using Injector + hard-coded prefix)
        Http.RequestBuilder request = Helpers.fakeRequest(GET, "/hello-prefixed/");
        Result result = route(server.app(), request);
        assertEquals(OK, result.status());
        assertEquals(result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "[prefixed] Hello");
    }
}
