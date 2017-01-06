/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
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
