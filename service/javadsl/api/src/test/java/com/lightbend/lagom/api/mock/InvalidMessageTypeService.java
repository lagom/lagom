/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.mock;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface InvalidMessageTypeService extends Service {
  <Foo> ServiceCall<Foo, String> hello();

  @Override
  default Descriptor descriptor() {
    return named("/invalid-service").withCalls(restCall(Method.POST, "/hello", this::hello));
  }
}
