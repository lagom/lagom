package docs.services.server;

import docs.services.EchoService;
import docs.services.EchoServiceImpl;
import docs.services.HelloService;
import docs.services.HelloServiceImpl;

//#multi-service
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class MultiServiceModule extends AbstractModule implements ServiceGuiceSupport {

    protected void configure() {
        bindServices(
                serviceBinding(HelloService.class, HelloServiceImpl.class),
                serviceBinding(EchoService.class, EchoServiceImpl.class));
    }
}
//#multi-service
