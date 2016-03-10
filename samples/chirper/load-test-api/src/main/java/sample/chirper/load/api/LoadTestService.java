package sample.chirper.load.api;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import akka.stream.javadsl.Source;

public interface LoadTestService extends Service {

  /**
   * Example: src/test/resources/websocket-loadtest.html
   */
  ServiceCall<NotUsed, NotUsed, Source<String, ?>> startLoad();

  /**
   * Example: curl http://localhost:21360/loadHeadless -H
   * "Content-Type: application/json" -X POST -d '{"users":2000, "friends":5,
   * "chirps":200000, "clients":20, "parallelism":20}'
   */
  ServiceCall<NotUsed, TestParams, NotUsed> startLoadHeadless();

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("/loadtestservice").with(
        pathCall("/load", startLoad()),
        restCall(Method.POST, "/loadHeadless", startLoadHeadless())
      );
    // @formatter:on
  }
}
