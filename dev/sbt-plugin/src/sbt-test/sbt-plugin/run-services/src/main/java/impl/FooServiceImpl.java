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
  public ServiceCall<NotUsed, NotUsed, NotUsed> foo() {
    return (id, request) -> {
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }

  @Override
  public ServiceCall<NotUsed, NotUsed, NotUsed> cassandra() {
    return (id, request) -> {
      return db.selectAll("SELECT now() FROM system.local;").thenApply(rows -> NotUsed.getInstance());
    };
  }
}
