package docs.home.actor;

//#module
import play.libs.akka.AkkaGuiceSupport;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class Worker2Module extends AbstractModule
    implements ServiceGuiceSupport, AkkaGuiceSupport {

  @Override
  protected void configure() {
    bindServices(serviceBinding(WorkerService2.class, WorkerService2Impl.class));

    bindActor(Worker2.class, "worker");
  }
}
//#module
