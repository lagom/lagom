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
        Http.RequestBuilder reqPing = Helpers.fakeRequest()
            .method(GET)
            .uri("/ping");

        Result pingRes = route(server.app(), reqPing);
        assertEquals(OK, pingRes.status());
        assertEquals(pingRes.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "ping");

        Http.RequestBuilder reqPong = Helpers.fakeRequest()
            .method(GET)
            .uri("/pong");

        Result pongRes = route(server.app(), reqPong);
        assertEquals(OK, pongRes.status());
         // FIXME this actually gets a PING! :O
        assertEquals(pongRes.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "pong");
    }


}
