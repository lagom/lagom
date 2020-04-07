/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.akka.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.example.hello.api.AkkaHttpService;

public class AkkaHttpModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindService(AkkaHttpService.class, AkkaHttpServiceImpl.class);
  }
}
