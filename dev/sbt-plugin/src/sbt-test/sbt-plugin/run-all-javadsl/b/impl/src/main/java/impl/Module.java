/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.BarService;
import api.FooService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

import com.typesafe.config.Config;

public class Module extends AbstractModule implements ServiceGuiceSupport {
	@Override
	protected void configure() {
		bindService(BarService.class, BarServiceImpl.class);
        bindClient(FooService.class);
		bind(OnStart.class).asEagerSingleton();
	}
}

class OnStart {

  @Inject
  public OnStart(Environment environment, Config configuration) {
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
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }
  }
}
