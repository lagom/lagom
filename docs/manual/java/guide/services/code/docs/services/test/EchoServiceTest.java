/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.test;

// #test
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.Arrays;
import org.junit.Test;
import akka.NotUsed;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber.Probe;
import akka.stream.testkit.javadsl.TestSink;

public class EchoServiceTest {

  @Test
  public void shouldEchoStream() throws Exception {
    withServer(
        defaultSetup().withCluster(false),
        server -> {
          EchoService service = server.client(EchoService.class);

          // Use a source that never terminates (concat Source.maybe) so we
          // don't close the upstream, which would close the downstream
          Source<String, NotUsed> input =
              Source.from(Arrays.asList("msg1", "msg2", "msg3")).concat(Source.maybe());
          Source<String, NotUsed> output =
              service.echo().invoke(input).toCompletableFuture().get(5, SECONDS);
          Probe<String> probe =
              output.runWith(TestSink.probe(server.system()), server.materializer());
          probe.request(10);
          probe.expectNext("msg1");
          probe.expectNext("msg2");
          probe.expectNext("msg3");
          probe.cancel();
        });
  }
}
// #test
