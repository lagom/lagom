/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import api.FooService;

public class FooServiceImpl implements FooService {

  @Override
  public ServiceCall<NotUsed, NotUsed> foo() {
    return request -> CompletableFuture.completedFuture(NotUsed.getInstance());
  }
}
