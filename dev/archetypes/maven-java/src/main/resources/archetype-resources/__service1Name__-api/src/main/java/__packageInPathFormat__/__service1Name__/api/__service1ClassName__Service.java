/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service1Name}.api;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

/**
 * The ${service1ClassName} service interface.
 * <p>
 * This describes everything that Lagom needs to know about how to serve and
 * consume the ${service1ClassName}.
 */
public interface ${service1ClassName}Service extends Service {

  /**
   * Example: curl http://localhost:9000/api/${service1Name}/Alice
   */
  ServiceCall<NotUsed, String> hello(String id);


  /**
   * Example: curl -H "Content-Type: application/json" -X POST -d '{"message":
   * "Hi"}' http://localhost:9000/api/${service1Name}/Alice
   */
  ServiceCall<GreetingMessage, Done> useGreeting(String id);

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("${service1Name}").withCalls(
        pathCall("/api/${service1Name}/:id",  this::hello),
        pathCall("/api/${service1Name}/:id", this::useGreeting)
      ).withAutoAcl(true);
    // @formatter:on
  }
}
