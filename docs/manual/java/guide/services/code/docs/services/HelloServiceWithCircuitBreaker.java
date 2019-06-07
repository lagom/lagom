/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloServiceWithCircuitBreaker extends Service {
  ServiceCall<String, String> sayHi();

  ServiceCall<String, String> hiAgain();

  // @formatter:off
  // #descriptor
  @Override
  default Descriptor descriptor() {
    return named("hello")
        .withCalls(
            namedCall("hi", this::sayHi),
            namedCall("hiAgain", this::hiAgain)
                .withCircuitBreaker(CircuitBreaker.identifiedBy("hello2")));
  }
  // #descriptor
  // @formatter:on
}
