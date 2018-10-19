package com.lightbend.stream.impl;

import akka.Done;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class StreamRepository {
  private final CassandraSession uninitialisedSession;

  // Will return the session when the Cassandra tables have been successfully created
  private volatile CompletableFuture<CassandraSession> initialisedSession;

  @Inject
  public StreamRepository(CassandraSession uninitialisedSession) {
    this.uninitialisedSession = uninitialisedSession;
    // Eagerly create the session
    session();
  }

  private CompletionStage<CassandraSession> session() {
    // If there's no initialised session, or if the initialised session future completed
    // with an exception, then reinitialise the session and attempt to create the tables
    if (initialisedSession == null || initialisedSession.isCompletedExceptionally()) {
      initialisedSession = uninitialisedSession.executeCreateTable(
          "CREATE TABLE IF NOT EXISTS greeting_message (name text PRIMARY KEY, message text)"
      ).thenApply(done -> uninitialisedSession).toCompletableFuture();
    }
    return initialisedSession;
  }

  public CompletionStage<Done> updateMessage(String name, String message) {
    return session().thenCompose(session ->
        session.executeWrite("INSERT INTO greeting_message (name, message) VALUES (?, ?)",
            name, message)
    );
  }

  public CompletionStage<Optional<String>> getMessage(String name) {
    return session().thenCompose(session ->
        session.selectOne("SELECT message FROM greeting_message WHERE name = ?", name)
    ).thenApply(maybeRow -> maybeRow.map(row -> row.getString("message")));
  }
}
