package docs.home.actor;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import static com.lightbend.lagom.javadsl.api.Service.*;

import akka.NotUsed;
import akka.stream.javadsl.Source;

public interface WorkerService2 extends Service {

  ServiceCall<NotUsed, Job, JobAccepted> doWork();

  ServiceCall<NotUsed, NotUsed, Source<JobStatus, ?>> status();

  @Override
  default Descriptor descriptor() {
    return named("/worker").with(
      call(doWork()),
      call(status())
    );
  }
}
