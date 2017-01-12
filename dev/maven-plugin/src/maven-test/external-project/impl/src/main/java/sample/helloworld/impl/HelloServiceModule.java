/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package sample.helloworld.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import sample.helloworld.api.HelloService;

/**
 * The module that binds the HelloService so that it can be served.
 */
public class HelloServiceModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindServices(serviceBinding(HelloService.class, HelloServiceImpl.class));
  }
}
