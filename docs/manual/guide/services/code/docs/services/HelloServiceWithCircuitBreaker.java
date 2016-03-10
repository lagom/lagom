package docs.services;


import com.lightbend.lagom.javadsl.api.Descriptor.CircuitBreakerId;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloServiceWithCircuitBreaker extends Service {
  ServiceCall<NotUsed, String, String> sayHi();

  ServiceCall<NotUsed, String, String> hiAgain();

  // @formatter:off
  //#descriptor
  @Override
  default Descriptor descriptor() {
      return named("hello").with(
        namedCall("hi", sayHi()),
        namedCall("hiAgain", hiAgain())
         .withCircuitBreaker(new CircuitBreakerId("hello2"))
      );
  }
  //#descriptor
  // @formatter:on
}
