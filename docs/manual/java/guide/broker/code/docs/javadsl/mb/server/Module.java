package docs.javadsl.mb.server;

import docs.javadsl.mb.AnotherService;
import docs.javadsl.mb.HelloService;
import docs.javadsl.mb.HelloServiceImpl;

import docs.javadsl.mb.AnotherServiceImpl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class Module extends AbstractModule implements ServiceGuiceSupport {

    protected void configure() {
        bindServices(serviceBinding(HelloService.class, HelloServiceImpl.class));
        bindServices(serviceBinding(AnotherService.class, AnotherServiceImpl.class));
    }
}
