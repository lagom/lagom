package docs.mb.server;

import docs.mb.HelloService;
import docs.mb.HelloServiceImpl;

import docs.mb.AnotherService;
import docs.mb.AnotherServiceImpl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class Module extends AbstractModule implements ServiceGuiceSupport {

    protected void configure() {
        bindServices(serviceBinding(HelloService.class, HelloServiceImpl.class));
        bindServices(serviceBinding(AnotherService.class, AnotherServiceImpl.class));
    }
}
