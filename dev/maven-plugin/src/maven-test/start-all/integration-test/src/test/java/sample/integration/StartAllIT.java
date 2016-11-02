package sample.integration;

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
import sample.hellostream.api.HelloStream;
import sample.helloworld.api.GreetingMessage;
import sample.helloworld.api.HelloService;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class StartAllIT {

    private static final String SERVICE_GATEWAY_URI = "http://localhost:16102";
    private static final String SERVICE_LOCATOR_URI = "http://localhost:16101";

    private static LagomClientFactory clientFactory;
    private static HelloService helloService;
    private static HelloStream helloStream;
    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void setup() {
        clientFactory = LagomClientFactory.create("integration-test", StartAllIT.class.getClassLoader());
        // One of the clients can use the service locator, the other can use the service gateway, to test them both.
        helloService = clientFactory.createClient(HelloService.class, URI.create(SERVICE_GATEWAY_URI));
        helloStream = clientFactory.createDevClient(HelloStream.class, URI.create(SERVICE_LOCATOR_URI));

        system = ActorSystem.create();
        mat = ActorMaterializer.create(system);
    }

    @Test
    public void helloWorld() throws Exception {
        String answer = await(helloService.hello("foo").invoke());
        assertEquals("Hello, foo!", answer);
        await(helloService.useGreeting("bar").invoke(new GreetingMessage("Hi")));
        String answer2 = await(helloService.hello("bar").invoke());
        assertEquals("Hi, bar!", answer2);
    }

    @Test
    public void helloStream() throws Exception {
        // Important to concat our source with a maybe, this ensures the connection doesn't get closed once we've
        // finished feeding our elements in, and then also to take 3 from the response stream, this ensures our
        // connection does get closed once we've received the 3 elements.
        Source<String, ?> response = await(helloStream.stream().invoke(
                Source.from(Arrays.asList("a", "b", "c"))
                        .concat(Source.maybe())));
        List<String> messages = await(response.take(3).runWith(Sink.seq(), mat));
        assertEquals(Arrays.asList("Hello, a!", "Hello, b!", "Hello, c!"), messages);
    }

    @Test
    public void devModeChanges() throws Exception {
        // First check that the file isn't already updated
        String answer = await(helloService.hello("lagom").invoke());
        assertEquals("Hello to you too!", answer);

        // Change the person it responds to
        File source = new File("../helloworld-impl/src/main/java/sample/helloworld/impl/HelloServiceImpl.java");
        System.out.println(source.getAbsolutePath());
        List<String> lines = Files.readAllLines(source.toPath());
        List<String> modifiedLines = lines.stream()
                .map(line -> line.replaceAll("\"lagom\"", "\"dude\""))
                .collect(Collectors.toList());
        Files.write(source.toPath(), modifiedLines);

        // Keep invoking until it's successful and returns what we expect
        doUntilSuccessful(20, 500, () ->
          assertEquals("Hello to you too!", await(helloService.hello("dude").invoke()))
        );
    }

    private void doUntilSuccessful(int maxTimes, long sleepMillis, Effect effect) throws Exception {
        try {
            effect.apply();
        } catch (Throwable t) {
            if (maxTimes <= 1) {
                throw t;
            } else {
                Thread.sleep(sleepMillis);
                doUntilSuccessful(maxTimes - 1, sleepMillis, effect);
            }
        }
    }

    private <T> T await(CompletionStage<T> future) throws Exception {
        return future.toCompletableFuture().get(30, TimeUnit.SECONDS);
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
