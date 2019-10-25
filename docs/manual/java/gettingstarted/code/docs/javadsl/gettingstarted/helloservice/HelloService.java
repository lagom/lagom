/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.gettingstarted.helloservice;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

// #helloservice-interface
public interface HelloService extends Service {

  ServiceCall<NotUsed, String> hello(String id);

  ServiceCall<GreetingMessage, Done> useGreeting(String id);

  @Override
  default Descriptor descriptor() {
    return named("helloservice")
        .withCalls(
            restCall(Method.GET, "/api/hello/:id", this::hello),
            pathCall("/api/hello/:id", this::useGreeting))
        .withAutoAcl(true);
  }
}
// #helloservice-interface
