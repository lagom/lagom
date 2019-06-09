/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;
import java.util.concurrent.CompletableFuture;
import akka.NotUsed;

@SuppressWarnings("unused")
public class StubDependencies {

  // #stub
  static class GreetingStub implements GreetingService {
    @Override
    public ServiceCall<String, String> greeting() {
      return req -> CompletableFuture.completedFuture("Hello");
    }
  }

  private final Setup setup =
      defaultSetup()
          .configureBuilder(b -> b.overrides(bind(GreetingService.class).to(GreetingStub.class)));

  // #stub
}
