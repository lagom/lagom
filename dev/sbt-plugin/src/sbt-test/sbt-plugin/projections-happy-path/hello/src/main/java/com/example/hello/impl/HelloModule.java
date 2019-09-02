package com.example.hello.impl;

import com.example.hello.impl.readsides.StartedProcessor;
import com.example.hello.impl.readsides.StoppedProcessor;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

import com.example.hello.api.HelloService;

public class HelloModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(HelloService.class, HelloServiceImpl.class);
        bind(StartedProcessor.class);
        bind(StoppedProcessor.class);
    }
}
