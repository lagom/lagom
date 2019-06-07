/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.test;

import static java.util.concurrent.CompletableFuture.completedFuture;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import akka.NotUsed;
import akka.stream.javadsl.Source;

public class EchoServiceImpl implements EchoService {

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo() {
    return req -> completedFuture(Source.from(java.util.Arrays.asList("msg1", "msg2", "msg3")));
  }
}
