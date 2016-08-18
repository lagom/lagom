/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.hellostream.impl;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import sample.helloworld.api.HelloService;
import sample.hellostream.api.HelloStream;

import javax.inject.Inject;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Implementation of the HelloString.
 */
public class HelloStreamImpl implements HelloStream {

  private final HelloService helloService;

  @Inject
  public HelloStreamImpl(HelloService helloService) {
    this.helloService = helloService;
  }

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> stream() {
    return hellos -> completedFuture(
        hellos.mapAsync(8, name -> helloService.hello(name).invoke()));
  }
}
