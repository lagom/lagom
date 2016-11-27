package api;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BazService extends Service {

  ServiceCall<NotUsed, String> baz();

  @Override
  default Descriptor descriptor() {
    return named("/c").withCalls(restCall(Method.GET,  "/baz",    this::baz)).withAutoAcl(true);
  }
}
