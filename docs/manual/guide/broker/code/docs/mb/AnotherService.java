package docs.mb;

import akka.NotUsed;

import com.lightbend.lagom.javadsl.api.*;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface AnotherService extends Service {
  ServiceCall<NotUsed, NotUsed> foo();
  @Override
  default Descriptor descriptor() {
    return named("anotherservice").withCalls(
        namedCall("/api/foo",  this::foo)
      );
  }
}
