/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.FooService;
import com.typesafe.config.Config;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class FooModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindService(FooService.class, FooServiceImpl.class);
    bind(FooOnStart.class).asEagerSingleton();
  }
}

class FooOnStart {

  @Inject
  public FooOnStart(Environment environment, Config configuration) {
    doOnStart(environment, configuration);
  }

  private void doOnStart(Environment environment, Config configuration) {
    try {
      // open for append
      FileWriter writer = new FileWriter(environment.getFile("target/reload.log"), true);
      writer.write(new Date() + " - reloaded\n");
      writer.close();

      if (configuration.hasPathOrNull("fail") && configuration.getBoolean("fail")) {
        throw new RuntimeException();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
