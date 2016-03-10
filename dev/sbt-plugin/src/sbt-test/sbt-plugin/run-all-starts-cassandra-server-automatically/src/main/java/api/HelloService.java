package api;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface HelloService extends Service {

  ServiceCall<String, NotUsed, String> hello();

  @Override
  default Descriptor descriptor() {
    return named("/helloservice").with(restCall(Method.GET, "/hello/:id", hello()))
            .withAutoAcl(true);
  }
}
