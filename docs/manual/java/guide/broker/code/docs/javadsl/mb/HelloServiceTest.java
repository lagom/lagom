package docs.javadsl.mb;


import org.junit.Test;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;


public class HelloServiceTest {

    private Setup setup = defaultSetup().configureBuilder(b ->
            b.overrides(bind(HelloService.class).to(HelloServiceImpl.class)));

    //#topic-test-publishing-into-a-topic
    @Test
    public void shouldEmitGreetingsMessageWhenHelloEntityEmitsEnEvent() {
        withServer(setup, server -> {
            HelloService client = server.client(HelloService.class);
            Source<GreetingMessage, ?> source = client.greetingsTopic().subscribe().atMostOnceSource();

            // use akka stream testkit
            TestSubscriber.Probe<GreetingMessage> probe = source.runWith(TestSink.probe(server.system()), server.materializer());

            // (not shown) use the client to cause events

            GreetingMessage actual =  probe.request(1).expectNext();
            // assert the actual message is what's expected.
        });
    }
    //#topic-test-publishing-into-a-topic
}
