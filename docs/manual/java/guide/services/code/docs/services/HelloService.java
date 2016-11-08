package docs.services;

//#hello-service
import com.lightbend.lagom.javadsl.api.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloService extends Service {
    ServiceCall<String, String> sayHello();

    default Descriptor descriptor() {
        return named("hello").withCalls(
                call(this::sayHello)
        );
    }
}
//#hello-service
