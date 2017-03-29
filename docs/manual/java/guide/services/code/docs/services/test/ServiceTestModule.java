package docs.services.test;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import docs.services.HelloService;
import docs.services.HelloServiceImpl;

public class ServiceTestModule extends AbstractModule implements ServiceGuiceSupport {

  @Override
  protected void configure() {
    bindServices(
      serviceBinding(EchoService.class, EchoServiceImpl.class),
      serviceBinding(HelloService.class, HelloServiceImpl.class)
    );
  }
}
