/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.services.server;

import docs.services.EchoService;
import docs.services.HelloService;
import docs.services.HelloServiceImpl;

// #bind-client
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class ServiceModule extends AbstractModule implements ServiceGuiceSupport {

  protected void configure() {
    bindService(HelloService.class, HelloServiceImpl.class);
    bindClient(EchoService.class);
  }
}
// #bind-client
