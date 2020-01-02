/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it.routers;

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
    server =
        startServer(
            defaultSetup()
                .withCluster(false)
                .withCassandra(false)
                .configureBuilder(b -> b.overrides(new AdditionalRoutersServiceModule())));
    materializer = server.materializer();
  }

  @AfterClass
  public static void tearDown() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  /** call the ping router (instance + bind dsl prefix) */
  @Test
  public void testInstanceRouterWithBindingPrefix() throws Exception {
    Http.RequestBuilder request = Helpers.fakeRequest(GET, "/ping/");
    Result result = route(server.app(), request);
    assertEquals(OK, result.status());
    assertEquals(
        result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "ping");
  }

  /** call the pong router (prefixed instance) */
  @Test
  public void testInstanceRouterWithPreConfiguredPrefix() throws Exception {
    Http.RequestBuilder request = Helpers.fakeRequest(GET, "/pong/");
    Result result = route(server.app(), request);
    assertEquals(OK, result.status());
    assertEquals(
        result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "pong");
  }

  /** call the echo router (router instantiated using Injector + bind dsl prefix) */
  @Test
  public void testInjectedRouterWithBindingPrefix() throws Exception {
    Http.RequestBuilder request = Helpers.fakeRequest(GET, "/hello/");
    Result result = route(server.app(), request);
    assertEquals(OK, result.status());
    assertEquals(
        result.body().consumeData(materializer).toCompletableFuture().get().utf8String(), "Hello");
  }

  /** call the echo router (router instantiated using Injector + hard-coded prefix) */
  @Test
  public void testInjectedRouterWithPreConfiguredPrefix() throws Exception {
    Http.RequestBuilder request = Helpers.fakeRequest(GET, "/hello-prefixed/");
    Result result = route(server.app(), request);
    assertEquals(OK, result.status());
    assertEquals(
        result.body().consumeData(materializer).toCompletableFuture().get().utf8String(),
        "[prefixed] Hello");
  }
}
