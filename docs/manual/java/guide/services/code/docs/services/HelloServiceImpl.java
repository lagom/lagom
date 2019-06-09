/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services;

// #hello-service-impl
import com.lightbend.lagom.javadsl.api.*;
import akka.NotUsed;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class HelloServiceImpl implements HelloService {

  public ServiceCall<String, String> sayHello() {
    return name -> completedFuture("Hello " + name);
  }
}
// #hello-service-impl
