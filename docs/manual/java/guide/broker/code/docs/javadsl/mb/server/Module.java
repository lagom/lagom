package docs.javadsl.mb.server;

import docs.javadsl.mb.*;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class Module extends AbstractModule implements ServiceGuiceSupport {

    protected void configure() {
        bindServices(serviceBinding(HelloService.class, HelloServiceImpl.class),
                serviceBinding(AnotherService.class, AnotherServiceImpl.class));
    }
}
