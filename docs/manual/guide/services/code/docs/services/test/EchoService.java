package docs.services.test;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

//#echo-service
public interface EchoService extends Service {

  ServiceCall<NotUsed, Source<String, NotUsed>, Source<String, NotUsed>> echo();

  default Descriptor descriptor() {
    return named("echo").with(
      namedCall("echo", echo())
    );
  }
}
//#echo-service
