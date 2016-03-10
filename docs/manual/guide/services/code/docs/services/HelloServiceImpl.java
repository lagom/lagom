package docs.services;

//#hello-service-impl
import com.lightbend.lagom.javadsl.api.*;
import akka.NotUsed;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class HelloServiceImpl implements HelloService {

    public ServiceCall<NotUsed, String, String> sayHello() {
        return (id, name) -> completedFuture("Hello " + name);
    }
}
//#hello-service-impl
