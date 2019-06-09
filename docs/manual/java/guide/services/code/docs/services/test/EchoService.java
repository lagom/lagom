/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.test;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

// #echo-service
public interface EchoService extends Service {

  ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo();

  default Descriptor descriptor() {
    return named("echo").withCalls(namedCall("echo", this::echo));
  }
}
// #echo-service
