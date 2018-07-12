/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

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

    @BeforeClass
    public static void setUp() {
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
    }

    @Test
    public void shouldRespondOnAdditionalRouters() throws Exception {
        Http.RequestBuilder reqPing = Helpers.fakeRequest()
            .method(GET)
            .uri("/ping");

        Result pingRes = route(server.app(), reqPing);
        assertEquals(OK, pingRes.status());

        Http.RequestBuilder reqPong = Helpers.fakeRequest()
            .method(GET)
            .uri("/pong");

        Result pongRes = route(server.app(), reqPong);
        assertEquals(OK, pongRes.status());
    }



}
