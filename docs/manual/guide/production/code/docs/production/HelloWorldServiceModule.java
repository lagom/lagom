package com.lightbend.lagom;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.lightbend.lagom.HelloWorldService;
import com.lightbend.lagom.HelloWorldServiceImpl;

//#content

public class HelloWorldServiceModule extends AbstractModule implements ServiceGuiceSupport {


    private Environment environment;


    @Inject
    public HelloWorldServiceModule(Environment environment, Configuration configuration) {
        this.environment = environment;
    }


    @Override
    protected void configure() {

        bindServices(serviceBinding(HelloWorldService.class, HelloWorldServiceImpl.class));


        if (environment.mode() == Mode.Prod()) {

            try {
                ZooKeeperServiceRegistry registry = new ZooKeeperServiceRegistry(
                        ZooKeeperServiceLocator.zkUri(),
                        ZooKeeperServiceLocator.zkServicesPath());
                registry.start();

                // create the service instance for the service discovery
                // needs to be held on to to be able to unregister the service on shutdown
                ServiceInstance<String> serviceInstance = ServiceInstance.<String>builder()
                        .name("helloWorld")
                        .id("helloWorld")
                        .address("localhost")
                        .port(8080)
                        .uriSpec(new UriSpec("{scheme}://{serviceAddress}:{servicePort}"))
                        .build();

                // register the service
                registry.register(serviceInstance);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }


    }
}
//#content
