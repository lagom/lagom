package docs.home.persistence;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import akka.stream.javadsl.Source;

import com.lightbend.lagom.javadsl.api.*;
import com.lightbend.lagom.javadsl.api.transport.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogService2 extends Service {

  ServiceCall<NotUsed, Source<PostSummary, ?>> getPostSummaries();

  @Override
  default Descriptor descriptor() {
    return named("/blogservice").withCalls(
      restCall(Method.GET, "/blogs", this::getPostSummaries)
    );
  }
}
