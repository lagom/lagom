/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb;

import com.lightbend.lagom.javadsl.api.*;
import akka.NotUsed;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface AnotherService extends Service {
  ServiceCall<NotUsed, NotUsed> audit();

  @Override
  default Descriptor descriptor() {
    return named("anotherservice").withCalls(namedCall("/api/audit", this::audit));
  }
}
