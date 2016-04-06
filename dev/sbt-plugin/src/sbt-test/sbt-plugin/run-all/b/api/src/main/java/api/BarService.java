package api;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BarService extends Service {

  ServiceCall<NotUsed, NotUsed, String> bar();

  @Override
  default Descriptor descriptor() {
    return named("/b").with(restCall(Method.GET,  "/bar",    bar())).withAutoAcl(true);
  }
}
