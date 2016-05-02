package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.BazService;

import akka.stream.javadsl.Source;

public class BazServiceImpl implements BazService {

  @Override
  public ServiceCall<NotUsed, String> baz() {
    return request -> CompletableFuture.completedFuture("ack baz");
  }
}
