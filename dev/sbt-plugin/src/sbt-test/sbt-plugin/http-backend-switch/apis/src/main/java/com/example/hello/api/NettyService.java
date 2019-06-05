/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.hello.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;

public interface NettyService extends Service {

  ServiceCall<NotUsed, String> hello();

  @Override
  default Descriptor descriptor() {
    return named("netty").withCalls(
        pathCall("/api/netty",  this::hello)
      ).withAutoAcl(true);
  }
}
