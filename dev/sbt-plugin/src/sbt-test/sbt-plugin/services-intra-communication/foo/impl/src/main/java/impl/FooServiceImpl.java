/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.FooService;
import api.BarService;

import akka.stream.javadsl.Source;

public class FooServiceImpl implements FooService {

  private final BarService bar;

  @Inject
  public FooServiceImpl(BarService bar) {
    this.bar = bar;
  }

  @Override
  public ServiceCall<NotUsed, String> foo() {
    return request -> bar.bar().invoke();
  }
}
