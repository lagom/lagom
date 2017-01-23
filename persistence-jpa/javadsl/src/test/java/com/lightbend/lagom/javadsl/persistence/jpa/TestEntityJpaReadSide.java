/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jpa;

import com.google.common.collect.ImmutableMap;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.TestEntity;
import org.pcollections.PSequence;

import javax.persistence.EntityManager;
import javax.persistence.Persistence;
import java.util.concurrent.CompletionStage;

public class TestEntityJpaReadSide {
    private static final String SELECT_COUNT = "SELECT COUNT(test) FROM TestJpaEntity test WHERE parentId = :parentId";
    private final JpaSession jpa;

    public TestEntityJpaReadSide(JpaSession jpa) {
        this.jpa = jpa;
    }

    public CompletionStage<Long> getAppendCount(String parentId) {
        return jpa.withTransaction(entityManager ->
                entityManager
                        .createQuery(SELECT_COUNT, Long.class)
                        .setParameter("parentId", parentId)
                        .getSingleResult()
        );
    }

    public static class TestEntityJpaReadSideProcessor extends ReadSideProcessor<TestEntity.Evt> {
        private final JpaReadSide readSide;

        public TestEntityJpaReadSideProcessor(JpaReadSide readSide) {
            this.readSide = readSide;
        }

        @Override
        public ReadSideHandler<TestEntity.Evt> buildHandler() {
            return readSide.<TestEntity.Evt>builder("test-entity-read-side")
                    .setGlobalPrepare(unused -> createSchema())
                    .setEventHandler(TestEntity.Appended.class, this::insertElement)
                    .build();
        }

        @Override
        public PSequence<AggregateEventTag<TestEntity.Evt>> aggregateTags() {
            return TestEntity.Evt.AGGREGATE_EVENT_SHARDS.allTags();
        }

        private void createSchema() {
            Persistence.generateSchema("default", ImmutableMap.of("hibernate.hbm2ddl.auto", "update"));
        }

        private void insertElement(EntityManager entityManager, TestEntity.Appended event) {
            TestJpaEntity element = new TestJpaEntity(event.getEntityId(), event.getElement());
            entityManager.persist(element);
        }
    }
}
