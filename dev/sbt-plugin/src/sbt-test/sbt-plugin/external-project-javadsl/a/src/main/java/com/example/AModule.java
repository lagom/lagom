package com.example;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class AModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    public void configure() {
        bindServices(serviceBinding(A.class, AImpl.class));
    }
}