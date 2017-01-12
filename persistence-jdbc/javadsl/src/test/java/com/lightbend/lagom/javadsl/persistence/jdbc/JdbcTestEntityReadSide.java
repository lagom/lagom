/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.TestEntity;
import org.pcollections.PSequence;

import javax.inject.Inject;
import java.sql.*;
import java.util.concurrent.CompletionStage;

public class JdbcTestEntityReadSide {

    private final JdbcSession session;

    @Inject
    public JdbcTestEntityReadSide(JdbcSession session) {
        this.session = session;
    }

    public CompletionStage<Long> getAppendCount(String id) {
        return session.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("select count from testcounts where id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("count");
                    } else {
                        return 0L;
                    }
                }
            }
        });
    }

    public static class TestEntityReadSideProcessor extends ReadSideProcessor<TestEntity.Evt> {

        private final JdbcReadSide readSide;

        @Inject
        public TestEntityReadSideProcessor(JdbcReadSide readSide) {
            this.readSide = readSide;
        }

        @Override
        public ReadSideHandler<TestEntity.Evt> buildHandler() {
            return readSide.<TestEntity.Evt>builder("test-entity-read-side")
                    .setGlobalPrepare(this::createTable)
                    .setEventHandler(TestEntity.Appended.class, this::updateCount)
                    .build();
        }

        private void createTable(Connection connection) throws SQLException {
            try (CallableStatement statement = connection.prepareCall("create table if not exists testcounts (id varchar primary key, count bigint)")) {
                statement.execute();
            }
        }

        private void updateCount(Connection connection, TestEntity.Appended event) throws SQLException {

            try (PreparedStatement statement = connection.prepareStatement("select count from testcounts where id = ?")) {
                statement.setString(1, event.getEntityId());
                try (ResultSet rs = statement.executeQuery()) {

                    if (rs.next()) {
                        long count = rs.getLong("count");
                        try (PreparedStatement update = connection.prepareStatement("update testcounts set count = ? where id = ?")) {
                            update.setLong(1, count + 1);
                            update.setString(2, event.getEntityId());
                            update.execute();
                        }
                    } else {
                        try (PreparedStatement update = connection.prepareStatement("insert into testcounts values (?, 1)")) {
                            update.setString(1, event.getEntityId());
                            update.execute();
                        }

                    }
                }
            }
        }

        @Override
        public PSequence<AggregateEventTag<TestEntity.Evt>> aggregateTags() {
            return TestEntity.Evt.AGGREGATE_EVENT_SHARDS.allTags();
        }
    }
}
