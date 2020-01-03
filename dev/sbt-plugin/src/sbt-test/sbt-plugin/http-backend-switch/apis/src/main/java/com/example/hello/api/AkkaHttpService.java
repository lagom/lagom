/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.hello.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;

public interface AkkaHttpService extends Service {

  ServiceCall<NotUsed, String> hello();

  @Override
  default Descriptor descriptor() {
    return named("akka-http").withCalls(
        pathCall("/api/akka",  this::hello)
      ).withAutoAcl(true);
  }
}
