package docs.home.actor;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface WorkerService extends Service {

  ServiceCall<Job, JobAccepted> doWork();

  @Override
  default Descriptor descriptor() {
    return named("/worker").withCalls(
      call(this::doWork)
    );
  }
}
