/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.FooService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;

import com.typesafe.config.Config;

public class Module extends AbstractModule implements ServiceGuiceSupport {
	@Override
	protected void configure() {
		bind(OnStart.class).asEagerSingleton();
	}
}

class OnStart {

  public static String CASSANDRA_JOURNAL_PORT            = "cassandra-journal.port";
  public static String CASSANDRA_SNAPSHOT_STORE_PORT     = "cassandra-snapshot-store.port";
  public static String LAGOM_CASSANDRA_READ_PORT         = "lagom.persistence.read-side.cassandra.port";

  public static String INTERNAL_ACTOR_SYSTEM_NAME        = "lagom.akka.dev-mode.actor-system.name";
  public static String APPLICATION_ACTOR_SYSTEM_NAME     = "play.akka.actor-system";

  @Inject
  public OnStart(Environment environment, Config configuration) {
  	dumpInjectedConfig(environment, configuration);
  }

  private void dumpInjectedConfig(Environment environment, Config configuration) {
    ArrayList<String> keys = new ArrayList<>(Arrays.asList(
            CASSANDRA_JOURNAL_PORT,
            CASSANDRA_SNAPSHOT_STORE_PORT,
            LAGOM_CASSANDRA_READ_PORT,
            INTERNAL_ACTOR_SYSTEM_NAME,
            APPLICATION_ACTOR_SYSTEM_NAME
    ));

    try(FileWriter writer = new FileWriter(environment.getFile("target/injected-config.conf"), true)) {
      for(String key: keys) {
        String value = configuration.getString(key);
        writer.write(key + "="+value+"\n");
      }
    }
    catch(IOException e) {
      throw new RuntimeException(e);
    }
  }
}
