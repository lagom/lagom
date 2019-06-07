/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.test;

import docs.services.HelloService;

// #test
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HelloServiceTest {

  @Test
  public void shouldSayHello() throws Exception {
    withServer(
        defaultSetup(),
        server -> {
          HelloService service = server.client(HelloService.class);

          String msg = service.sayHello().invoke("Alice").toCompletableFuture().get(5, SECONDS);
          assertEquals("Hello Alice", msg);
        });
  }
}
// #test
