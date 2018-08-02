/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it.mocks;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.lightbend.lagom.javadsl.testkit.ServiceTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.mvc.Http;
import play.mvc.Result;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import play.test.*;

import java.util.concurrent.ExecutionException;

import static play.test.Helpers.*;

public class AdditionalRoutersServiceTest {

    private static ServiceTest.TestServer server;

    private static ActorSystem system;
    private static ActorMaterializer materializer;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create();
        materializer = ActorMaterializer.create(system);
        server = startServer(defaultSetup()
            .withCluster(false).withCassandra(false)
            .configureBuilder(b -> b.bindings(new AdditionalRoutersServiceModule()))
        );
    }

    @AfterClass
    public static void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (materializer != null) {
            materializer.shutdown();
            materializer = null;
        }
        if (system != null) {
            system.terminate();
            system = null;
        }
    }

    @Test
    public void shouldRespondOnAdditionalRouters() throws Exception {

        {
            Http.RequestBuilder request = Helpers.fakeRequest(GET, "/ping/");
            Result result = route(server.app(), request);
            assertEquals(OK, result.status());
            assertEquals(result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "ping");
        }

        {
            Http.RequestBuilder request = Helpers.fakeRequest(GET, "/pong/");
            Result result = route(server.app(), request);
            assertEquals(OK, result.status());
            assertEquals(result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "pong");
        }

        {
            Http.RequestBuilder request = Helpers.fakeRequest(GET, "/echo/");
            Result result = route(server.app(), request);
            assertEquals(OK, result.status());
            assertEquals(result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "Hello");
        }
    }



}
