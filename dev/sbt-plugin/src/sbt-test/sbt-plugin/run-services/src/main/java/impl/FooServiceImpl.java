/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.FooService;

import akka.stream.javadsl.Source;

public class FooServiceImpl implements FooService {

  private final CassandraSession db;

  @Inject
  public FooServiceImpl(CassandraSession db) {
    this.db = db;
  }

  @Override
  public ServiceCall<NotUsed, NotUsed> foo() {
    return request -> CompletableFuture.completedFuture(NotUsed.getInstance());
  }

  @Override
  public ServiceCall<NotUsed, NotUsed> cassandra() {
    return request -> db.selectAll("SELECT now() FROM system.local;").thenApply(rows -> NotUsed.getInstance());
  }
}
