/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service2Name}.api;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.namedCall;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

/**
 * The ${service2Name} interface.
 * <p>
 * This describes everything that Lagom needs to know about how to serve and
 * consume the HelloStream service.
 */
public interface ${service2ClassName}Service extends Service {

  ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> stream();

  @Override
  default Descriptor descriptor() {
    return named("${service2Name}").withCalls(namedCall("${service2Name}", this::stream))
      .withAutoAcl(true);
  }
}
