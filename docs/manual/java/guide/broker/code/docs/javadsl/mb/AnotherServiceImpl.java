package docs.javadsl.mb;

import akka.Done;
import akka.NotUsed;
import akka.stream.javadsl.Flow;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import javax.inject.Inject;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class AnotherServiceImpl implements AnotherService {

    //#inject-service
    private final HelloService helloService;

    @Inject
    public AnotherServiceImpl(HelloService helloService) {
        this.helloService = helloService;
    }
    //#inject-service

    public ServiceCall<NotUsed, NotUsed> audit() {
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
