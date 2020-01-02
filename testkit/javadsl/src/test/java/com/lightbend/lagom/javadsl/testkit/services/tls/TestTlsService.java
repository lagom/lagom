/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit.services.tls;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import static com.lightbend.lagom.javadsl.api.Service.*;

/** */
public interface TestTlsService extends Service {

  ServiceCall<NotUsed, String> sampleCall();

  @Override
  default Descriptor descriptor() {
    return named("java-test-tls").withCalls(restCall(Method.GET, "/api/sample", this::sampleCall));
  }
}
