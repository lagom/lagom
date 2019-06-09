/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.test;

import docs.services.HelloService;

// #test
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class AdvancedHelloServiceTest {

  private static TestServer server;

  @BeforeClass
  public static void setUp() {
    server = startServer(defaultSetup().withCluster(false));
  }

  @AfterClass
  public static void tearDown() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  @Test
  public void shouldSayHello() throws Exception {
    HelloService service = server.client(HelloService.class);
    String msg = service.sayHello().invoke("Alice").toCompletableFuture().get(5, SECONDS);
    assertEquals("Hello Alice", msg);
  }

  @Test
  public void shouldSayHelloAgain() throws Exception {
    HelloService service = server.client(HelloService.class);
    String msg = service.sayHello().invoke("Bob").toCompletableFuture().get(5, SECONDS);
    assertEquals("Hello Bob", msg);
  }
}
// #test
