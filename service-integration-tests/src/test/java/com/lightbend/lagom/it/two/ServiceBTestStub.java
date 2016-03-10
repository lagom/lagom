/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.two;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;

import akka.NotUsed;

public class ServiceBTestStub implements ServiceB {

  @Override
  public ServiceCall<NotUsed, String, String> helloB() {
    return (id, req) -> CompletableFuture.completedFuture("hello: " + req);
  }
}
