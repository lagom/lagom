/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

// #hello-service
import com.lightbend.lagom.javadsl.api.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloService extends Service {
  ServiceCall<String, String> sayHello();

  default Descriptor descriptor() {
    return named("hello").withCalls(call(this::sayHello));
  }
}
// #hello-service
