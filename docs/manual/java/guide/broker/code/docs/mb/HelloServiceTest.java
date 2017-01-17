package docs.mb;


import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import org.junit.Test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;


public class HelloServiceTest {

    //#topic-test-publishing-into-a-topic
    @Test
    public void shouldEmitGreetingsMessageWhenHelloEntityEmitsEnEvent() {
        withServer(defaultSetup(), server -> {
            HelloService client = server.client(HelloService.class);
            Source<GreetingMessage, ?> source = client.greetingsTopic().subscribe().atMostOnceSource();

            // (not shown) use the client to cause events

            GreetingMessage actual =  source.probe.request(1).expectNext();
            // assert the actual message is what's expected.
        }
    }
    //#topic-test-publishing-into-a-topic
}
