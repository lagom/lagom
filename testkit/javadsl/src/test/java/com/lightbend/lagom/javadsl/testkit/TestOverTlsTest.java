/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
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

// This is a complement to TestOverTlsSpec so we can embed some java code in theh docs. The actual unit test of
// the feature to use SSL in tests is on TestOverTlsSpec.
public class TestOverTlsTest {

    @Test
    public void testOverTlsWithCustomClient() {
        ServiceTest.withServer(
            ServiceTest.defaultSetup()
                .withCluster(false)
                .withSsl()
                        .configureBuilder(builder ->
                                builder.bindings(new TestTlsServiceModule())), server -> {

                WSClient wsClient = buildCustomWS(server.clientSslContext().get());
                String response = wsClient
                    .url("https://localhost:"+server.portSsl().get()+ "/api/sample")
                    .get()
                    .thenApply(WSResponse::getBody)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertEquals("sample response", response);
        });
    }


    private WSClient buildCustomWS(SSLContext sslContext){
        // Set up Akka
        String name = "test-client";
        ActorSystem system = ActorSystem.create(name);
        ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
        ActorMaterializer materializer = ActorMaterializer.create(settings, system, name);

        // Setup the client to use the custom SSLContext
        JdkSslContext jdkSslContext = new JdkSslContext(sslContext, true, ClientAuth.NONE);
        AsyncHttpClientConfig asyncHttpClientConfig =
            new DefaultAsyncHttpClientConfig.Builder()
                .setSslContext(jdkSslContext)
                .build();
        AsyncHttpClient asyncHttpClient =
            new DefaultAsyncHttpClient(asyncHttpClientConfig);


        // Set up WSClient instance directly from asynchttpclient.
        return new AhcWSClient(
            asyncHttpClient,
            materializer
        );
    }
}
