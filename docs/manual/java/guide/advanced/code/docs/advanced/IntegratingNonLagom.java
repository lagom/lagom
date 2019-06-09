/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.advanced;

import com.lightbend.lagom.javadsl.client.integration.LagomClientFactory;
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import docs.services.HelloService;
import org.junit.Test;
import akka.actor.ActorSystem;
import akka.stream.Materializer;
import java.net.URI;

public class IntegratingNonLagom {

  @Test
  public void testClientFactory() {
    withServer(
        defaultSetup(),
        server -> {
          // #create-factory
          LagomClientFactory clientFactory =
              LagomClientFactory.create("legacy-system", LagomClientFactory.class.getClassLoader());
          // #create-factory

          URI helloServiceUri = URI.create("http://localhost:" + server.port());

          // #create-client
          HelloService serviceClient =
              clientFactory.createClient(HelloService.class, helloServiceUri);
          // #create-client

          assertEquals(
              "Hello world",
              serviceClient.sayHello().invoke("world").toCompletableFuture().get(10, SECONDS));

          // #close-factory
          clientFactory.close();
          // #close-factory
        });
  }

  @Test
  public void testClientFactoryWithExternalActorSystem() {
    withServer(
        defaultSetup(),
        server -> {
          ActorSystem actorSystem = server.system();
          Materializer materializer = server.materializer();
          // #create-factory-external-actor-system
          LagomClientFactory clientFactory =
              LagomClientFactory.create(
                  "legacy-system",
                  LagomClientFactory.class.getClassLoader(),
                  actorSystem,
                  materializer);
          // #create-factory-external-actor-system

          URI helloServiceUri = URI.create("http://localhost:" + server.port());

          // #create-client
          HelloService serviceClient =
              clientFactory.createClient(HelloService.class, helloServiceUri);
          // #create-client

          assertEquals(
              "Hello world",
              serviceClient.sayHello().invoke("world").toCompletableFuture().get(10, SECONDS));

          // #close-factory
          clientFactory.close();
          // #close-factory
        });
  }

  private void devMode(LagomClientFactory clientFactory) {
    boolean isDevelopment = false;
    URI helloServiceUri = URI.create("http://localhost:8000");

    // #dev-mode
    HelloService helloService;
    if (isDevelopment) {
      helloService = clientFactory.createDevClient(HelloService.class);
    } else {
      helloService = clientFactory.createClient(HelloService.class, helloServiceUri);
    }
    // #dev-mode
  }
}
