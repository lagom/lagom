/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
package com.example.netty.impl;

import com.example.hello.api.NettyService;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class NettyModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindService(NettyService.class, NettyServiceImpl.class);
  }
}
