package com.example.hello.impl;

import com.example.hello.api.HelloService;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;

public class HelloServiceTest {

    public final Logger logger = LoggerFactory.getLogger(HelloServiceTest.class);

    @Test
    public void shouldStorePersonalizedGreeting() {
        withServer(defaultSetup().withCassandra(), server -> {
            HelloService service = server.client(HelloService.class);

            String greetingMessage = "Hi";
            service.useGreeting("Alice", greetingMessage).invoke().toCompletableFuture().get(5, SECONDS);
            String msg2 = service.hello("Alice").invoke().toCompletableFuture().get(5, SECONDS);
            // Assert the event was recorded in the database
            assertTrue(msg2.contains("Hi, Alice!"));
            // Immediately after posting the greeting, the started report is still returning defautl values
            assertTrue(msg2.contains("Started reports: default-projected-message"));

            eventually(new FiniteDuration(15, SECONDS),
                () -> {
                    SECONDS.sleep(1);

                    String msg3 = service.hello("Alice").invoke().toCompletableFuture().get(5, SECONDS);
                    // eventually, the started report returns Hi
                    assertTrue(msg3.contains("Started reports: Hi"));
                    // ... but the stopped processor still reports default values.
                    assertTrue(msg3.contains("Stopped reports: default-projected-message"));
                }
            );
        });
    }
}
