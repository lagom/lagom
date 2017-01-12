/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.it;

import akka.actor.ActorSystem;
import akka.japi.Effect;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.client.integration.LagomClientFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import ${package}.${service2Name}.api.${service2ClassName}Service;
import ${package}.${service1Name}.api.GreetingMessage;
import ${package}.${service1Name}.api.${service1ClassName}Service;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ${service2ClassName}IT {

    private static final String SERVICE_LOCATOR_URI = "http://localhost:8000";

    private static LagomClientFactory clientFactory;
    private static ${service1ClassName}Service ${service1Name}Service;
    private static ${service2ClassName}Service ${service2Name}Service;
    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void setup() {
        clientFactory = LagomClientFactory.create("integration-test", ${service2ClassName}IT.class.getClassLoader());
        // One of the clients can use the service locator, the other can use the service gateway, to test them both.
        ${service1Name}Service = clientFactory.createDevClient(${service1ClassName}Service.class, URI.create(SERVICE_LOCATOR_URI));
        ${service2Name}Service = clientFactory.createDevClient(${service2ClassName}Service.class, URI.create(SERVICE_LOCATOR_URI));

        system = ActorSystem.create();
        mat = ActorMaterializer.create(system);
    }

    @Test
    public void helloWorld() throws Exception {
        String answer = await(${service1Name}Service.hello("foo").invoke());
        assertEquals("Hello, foo!", answer);
        await(${service1Name}Service.useGreeting("bar").invoke(new GreetingMessage("Hi")));
        String answer2 = await(${service1Name}Service.hello("bar").invoke());
        assertEquals("Hi, bar!", answer2);
    }

    @Test
    public void helloStream() throws Exception {
        // Important to concat our source with a maybe, this ensures the connection doesn't get closed once we've
        // finished feeding our elements in, and then also to take 3 from the response stream, this ensures our
        // connection does get closed once we've received the 3 elements.
        Source<String, ?> response = await(${service2Name}Service.stream().invoke(
                Source.from(Arrays.asList("a", "b", "c"))
                        .concat(Source.maybe())));
        List<String> messages = await(response.take(3).runWith(Sink.seq(), mat));
        assertEquals(Arrays.asList("Hello, a!", "Hello, b!", "Hello, c!"), messages);
    }

    private <T> T await(CompletionStage<T> future) throws Exception {
        return future.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @AfterClass
    public static void tearDown() {
        if (clientFactory != null) {
            clientFactory.close();
        }
        if (system != null) {
            system.terminate();
        }
    }




}
