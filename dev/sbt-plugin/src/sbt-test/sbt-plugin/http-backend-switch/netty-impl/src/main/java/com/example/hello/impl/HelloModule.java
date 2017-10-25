/*
 *
 */
package com.example.hello.impl;

import com.example.hello.api.NettyService;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class HelloModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindService(NettyService.class, NettyServiceImpl.class);
  }
}
