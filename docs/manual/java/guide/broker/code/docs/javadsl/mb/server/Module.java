/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.mb.server;

import docs.javadsl.mb.*;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class Module extends AbstractModule implements ServiceGuiceSupport {

  protected void configure() {
    bindService(AnotherService.class, AnotherServiceImpl.class);
    bindClient(HelloService.class);
  }
}
