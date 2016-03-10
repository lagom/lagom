package docs.services.test;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface GreetingService extends Service {
    ServiceCall<NotUsed, String, String> greeting();

    default Descriptor descriptor() {
        return named("greeting").with(
          namedCall("greeting", greeting())
        );
    }
}
