/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.api.mock;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import java.util.UUID;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

public interface DefaultMethodWithParamsService extends Service {

  ServiceCall<UUID, String> hello(String name);

  default String serviceName(String param) {
    return param;
  }

  @Override
  default Descriptor descriptor() {
    return named(serviceName("my-service"))
        .withCalls(restCall(Method.GET, "/hello/:name", this::hello));
  }
}
