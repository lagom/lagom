package docs.services;

//#hello-service
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloService extends Service {
    ServiceCall<NotUsed, String, String> sayHello();

    default Descriptor descriptor() {
        return named("hello").with(
                call(sayHello())
        );
    }
}
//#hello-service
