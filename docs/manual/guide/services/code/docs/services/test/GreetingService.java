package docs.services.test;

import com.lightbend.lagom.javadsl.api.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface GreetingService extends Service {
    ServiceCall<String, String> greeting();

    default Descriptor descriptor() {
        return named("greeting").withCalls(
          namedCall("greeting", this::greeting)
        );
    }
}
