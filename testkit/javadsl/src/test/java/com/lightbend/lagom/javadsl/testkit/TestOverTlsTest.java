/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.typesafe.sslconfig.ssl.SSLContextBuilder;
import org.junit.Test;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.libs.ws.ahc.AhcWSClient;
import play.shaded.ahc.io.netty.handler.ssl.ClientAuth;
import play.shaded.ahc.io.netty.handler.ssl.JdkSslContext;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig;

import javax.net.ssl.SSLContext;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

// This is a complement to TestOverTlsSpec so we can embed some java code in the docs. The actual
// unit test of
// the feature to use SSL in tests is on TestOverTlsSpec.
public class TestOverTlsTest {

  // #tls-test-service
  @Test
  public void testOverTlsWithCustomClient() {
    ServiceTest.withServer(
        ServiceTest.defaultSetup()
            .withCluster(false)
            .withSsl()
            .configureBuilder(builder -> builder.bindings(new TestTlsServiceModule())),
        server -> {
          SSLContext sslContext = server.clientSslContext().get();
          // Builds an instance of a WSClient with the provided SSLContext so
          // the fake SSLContext prepared by Lagom's testkit is used.
          WSClient wsClient = buildCustomWS(sslContext, server.system());
          // use `localhost` as authority
          String url = "https://localhost:" + server.portSsl().get() + "/api/sample";
          String response =
              wsClient
                  .url(url)
                  .get()
                  .thenApply(WSResponse::getBody)
                  .toCompletableFuture()
                  .get(5, TimeUnit.SECONDS);

          assertEquals("sample response", response);
        });
  }
  // #tls-test-service

  private WSClient buildCustomWS(SSLContext sslContext, ActorSystem system) {
    // Set up Akka
    String name = "test-client";
    ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
    ActorMaterializer materializer = ActorMaterializer.create(settings, system, name);

    // Setup the client to use the custom SSLContext
    JdkSslContext jdkSslContext = new JdkSslContext(sslContext, true, ClientAuth.NONE);
    AsyncHttpClientConfig asyncHttpClientConfig =
        new DefaultAsyncHttpClientConfig.Builder().setSslContext(jdkSslContext).build();
    AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig);

    // Set up WSClient instance directly from asynchttpclient.
    return new AhcWSClient(asyncHttpClient, materializer);
  }
}
