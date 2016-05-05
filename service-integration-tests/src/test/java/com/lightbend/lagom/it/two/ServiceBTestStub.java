/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.two;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;

public class ServiceBTestStub implements ServiceB {

  @Override
  public ServiceCall<String, String> helloB() {
    return req -> CompletableFuture.completedFuture("hello: " + req);
  }
}
