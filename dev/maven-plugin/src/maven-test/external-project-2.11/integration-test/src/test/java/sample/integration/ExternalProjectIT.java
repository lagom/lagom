package sample.integration;

import com.lightbend.lagom.javadsl.client.integration.LagomClientFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import sample.helloworld.api.HelloService;

import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ExternalProjectIT {

    private static LagomClientFactory clientFactory;
    private static HelloService helloService;

    @BeforeClass
    public static void setup() {
        clientFactory = LagomClientFactory.create("integration-test", ExternalProjectIT.class.getClassLoader());
        helloService = clientFactory.createDevClient(HelloService.class, URI.create("http://localhost:9008"));
    }

    @Test
    public void helloWorld() throws Exception {
        String answer = await(helloService.hello("foo").invoke());
        assertEquals("Hello, foo!", answer);
    }

    private <T> T await(CompletionStage<T> future) throws Exception {
        return future.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @AfterClass
    public static void tearDown() {
        if (clientFactory != null) {
            clientFactory.close();
        }
    }




}
