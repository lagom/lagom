package docs.mb;

import com.lightbend.lagom.javadsl.api.*;
import akka.NotUsed;
import akka.Done;
import akka.stream.javadsl.Flow;
import static java.util.concurrent.CompletableFuture.completedFuture;
import javax.inject.Inject;

public class AnotherServiceImpl implements AnotherService {

    //#inject-service
    private final HelloService helloService;

    @Inject
    public AnotherServiceImpl(HelloService helloService) {
        this.helloService = helloService;
    }
    //#inject-service

    public ServiceCall<NotUsed, NotUsed> foo() {
        //#subscribe-to-topic
        helloService.greetingsTopic()
            .subscribe() // <-- you get back a Subscriber instance
            .atLeastOnce(Flow.fromFunction((GreetingMessage message) -> {
                return doSomethingWithTheMessage(message);
            }));
        //#subscribe-to-topic
        return name -> completedFuture(NotUsed.getInstance());
    }

    private Done doSomethingWithTheMessage(GreetingMessage message) {
        throw new UnsupportedOperationException("Missing implementation");
    }
}
