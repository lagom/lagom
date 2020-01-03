/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package api;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface FooService extends Service {

  ServiceCall<NotUsed, NotUsed> foo();
  ServiceCall<NotUsed, NotUsed> cassandra();

  @Override
  default Descriptor descriptor() {
    return named("fooservice").withCalls(
            restCall(Method.GET,  "/foo",    this::foo),
            restCall(Method.GET,  "/foo/cassandra",    this::cassandra)
    ).withAutoAcl(true);
  }
}
