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

  public static String CASSANDRA_JOURNAL_KEYSPACE        = "cassandra-journal.keyspace";
  public static String CASSANDRA_JOURNAL_PORT            = "cassandra-journal.port";
  public static String CASSANDRA_SNAPSHOT_STORE_KEYSPACE = "cassandra-snapshot-store.keyspace";
  public static String CASSANDRA_SNAPSHOT_STORE_PORT     = "cassandra-snapshot-store.port";
  public static String LAGOM_CASSANDRA_READ_KEYSPACE     = "lagom.persistence.read-side.cassandra.keyspace";
  public static String LAGOM_CASSANDRA_READ_PORT         = "lagom.persistence.read-side.cassandra.port";

  @Inject
  public OnStart(Environment environment, Config configuration) {
    dumpInjectedCassandraConfig(environment, configuration);
  }

  private void dumpInjectedCassandraConfig(Environment environment, Config configuration) {
    ArrayList<String> keys = new ArrayList<>(Arrays.asList(CASSANDRA_JOURNAL_KEYSPACE, CASSANDRA_JOURNAL_PORT,
      CASSANDRA_SNAPSHOT_STORE_KEYSPACE, CASSANDRA_SNAPSHOT_STORE_PORT,
      LAGOM_CASSANDRA_READ_KEYSPACE, LAGOM_CASSANDRA_READ_PORT));

    try(FileWriter writer = new FileWriter(environment.getFile("target/injected-cassandra.conf"), true)) {
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
