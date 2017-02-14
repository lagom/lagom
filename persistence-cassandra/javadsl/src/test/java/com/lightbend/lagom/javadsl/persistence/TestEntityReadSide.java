/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import akka.Done;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import org.pcollections.PSequence;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class TestEntityReadSide {

  private final CassandraSession session;

  @Inject
  public TestEntityReadSide(CassandraSession session) {
    this.session = session;
  }

  public CompletionStage<Long> getAppendCount(String entityId) {
    return session.selectOne("SELECT count FROM testcounts WHERE id = ?", entityId).thenApply(maybeRow -> {
      if (maybeRow.isPresent()) {
        return maybeRow.get().getLong("count");
      } else {
        return 0L;
      }
    });
  }

  public static class TestEntityReadSideProcessor extends ReadSideProcessor<TestEntity.Evt> {
    private final CassandraReadSide readSide;
    private final CassandraSession session;

    volatile private PreparedStatement writeStmt;

    @Inject
    public TestEntityReadSideProcessor(CassandraReadSide readSide, CassandraSession session) {
      this.readSide = readSide;
      this.session = session;
    }

    @Override
    public ReadSideProcessor.ReadSideHandler<TestEntity.Evt> buildHandler() {
      return readSide.<TestEntity.Evt>builder("testoffsets")
              .setGlobalPrepare(this::createTable)
              .setPrepare(tag -> prepareWriteStmt())
              .setEventHandler(TestEntity.Appended.class, this::updateCount)
              .build();
    }

    private CompletionStage<List<BoundStatement>> updateCount(TestEntity.Appended event) {
      return session.selectOne("SELECT count FROM testcounts WHERE id = ?", event.getEntityId()).thenApply(maybeRow -> {
        long count;
        if (maybeRow.isPresent()) {
          count = maybeRow.get().getLong("count");
        } else {
          count = 0;
        }
        return Collections.singletonList(writeStmt.bind(count + 1, event.getEntityId()));
      });
    }

    private CompletionStage<Done> createTable() {
      return session.executeCreateTable(
              "CREATE TABLE IF NOT EXISTS testcounts ( " +
                      "id text, count bigint, PRIMARY KEY (id))");
    }

    private CompletionStage<Done> prepareWriteStmt() {
      return session.prepare("UPDATE testcounts SET count = ? WHERE id = ?").thenApply(ws -> {
        writeStmt = ws;
        return Done.getInstance();
      });
    }

    @Override
    public PSequence<AggregateEventTag<TestEntity.Evt>> aggregateTags() {
      return TestEntity.Evt.AGGREGATE_EVENT_SHARDS.allTags();
    }
  }

}
