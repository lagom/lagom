package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.FooService;

import akka.stream.javadsl.Source;

public class FooServiceImpl implements FooService {

  @Override
  public ServiceCall<NotUsed, NotUsed, NotUsed> foo() {
    return (id, request) -> {
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }
}
