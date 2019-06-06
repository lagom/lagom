/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.server;

import docs.services.HelloService;
import docs.services.HelloServiceImpl;

// #hello-service-binding
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class Module extends AbstractModule implements ServiceGuiceSupport {

  protected void configure() {
    bindService(HelloService.class, HelloServiceImpl.class);
  }
}
// #hello-service-binding
