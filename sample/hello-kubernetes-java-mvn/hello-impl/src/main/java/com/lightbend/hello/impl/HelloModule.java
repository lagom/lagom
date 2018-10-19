package com.lightbend.hello.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.lightbend.hello.api.HelloService;

/**
 * The module that binds the HelloService so that it can be served.
 */
public class HelloModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindService(HelloService.class, HelloServiceImpl.class);
  }
}
