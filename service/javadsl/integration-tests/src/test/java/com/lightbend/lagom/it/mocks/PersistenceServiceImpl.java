/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class PersistenceServiceImpl implements PersistenceService {
  private final PersistentEntityRegistry persistentEntityRegistry;
  private final PubSubRegistry pubSubRegistry;
  private final CassandraReadSide readSide;
  private final CassandraSession db;

  @Inject
  public PersistenceServiceImpl(PersistentEntityRegistry persistentEntityRegistry, PubSubRegistry pubSubRegistry, 
    CassandraReadSide readSide, CassandraSession db) {
    this.persistentEntityRegistry = persistentEntityRegistry;
    this.pubSubRegistry = pubSubRegistry;
    this.readSide = readSide;
    this.db = db;
  }

  @Override
  public ServiceCall<NotUsed, String> checkInjected() {
    return request -> {
      if (persistentEntityRegistry == null)
          throw new NullPointerException();
      if (pubSubRegistry == null)
          throw new NullPointerException();
      if (readSide == null)
        throw new NullPointerException();
      if (db == null)
        throw new NullPointerException();
      return CompletableFuture.completedFuture("ok");
    };
  }
  
  @Override
  public ServiceCall<NotUsed, String> checkCassandraSession() {
    return request -> {
      return db.executeCreateTable(
          "CREATE TABLE IF NOT EXISTS testcounts ( " +
          "  partition text, " +
          "  key text," +
          "  count bigint, " +
          "  PRIMARY KEY (partition, key)) ")
      .thenApply(session -> "ok");
    };
  }
}
