/*
 *
 */
package com.example.hello.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.example.hello.api.AkkaHttpService;

public class HelloModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindService(AkkaHttpService.class, AkkaHttpServiceImpl.class);
  }
}
