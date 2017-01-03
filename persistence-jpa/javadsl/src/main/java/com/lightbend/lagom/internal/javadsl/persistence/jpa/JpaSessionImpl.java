/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa;

import akka.Done;
import akka.actor.ActorSystem;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.SlickProvider;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.inject.ApplicationLifecycle;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.JFunction0;
import scala.concurrent.duration.FiniteDuration;
import slick.jdbc.JdbcBackend;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Singleton
public class JpaSessionImpl implements JpaSession {
    // TODO: Make these constants configuration parameters in the Lagom framework implementation
    private static final String PERSISTENCE_UNIT_NAME = "default";
    private static final FiniteDuration INIT_RETRY_INTERVAL = FiniteDuration.create(5, TimeUnit.SECONDS);
    private static final double INIT_RETRY_FACTOR = 1.0;
    private static final int INIT_RETRY_COUNT = 12;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final JdbcBackend.DatabaseDef slickDb;
    private final CompletionStage<EntityManagerFactory> factoryCompletionStage;

    @Inject
    public JpaSessionImpl(ActorSystem actorSystem, SlickProvider slick, ApplicationLifecycle lifecycle) {
        this.slickDb = slick.db();
        this.factoryCompletionStage = createEntityManagerFactory(actorSystem);
        lifecycle.addStopHook(this::close);
    }

    @Override
    public <T> CompletionStage<T> withTransaction(Function<EntityManager, T> block) {
        return withEntityManager(entityManager -> {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                T result = block.apply(entityManager);
                transaction.commit();
                return result;
            } finally {
                if (transaction.isActive()) transaction.rollback();
            }
        });
    }

    private CompletionStage<EntityManagerFactory> createEntityManagerFactory(ActorSystem actorSystem) {
        log.debug("Initializing JPA EntityManagerFactory with persistence unit name {}", PERSISTENCE_UNIT_NAME);
        Retry jpaInitializer = new Retry(INIT_RETRY_INTERVAL, INIT_RETRY_FACTOR, INIT_RETRY_COUNT) {
            @Override
            public void onRetry(Throwable throwable, FiniteDuration delay, int remainingRetries) {
                log.warn("Exception while initializing JPA EntityManagerFactory", throwable);
                log.info("Will retry initializing JPA EntityManagerFactory {} times in {}", remainingRetries, delay);
            }
        };
        return jpaInitializer.retry(
                () -> Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME),
                slickDb.ioExecutionContext(),
                actorSystem.scheduler()
        ).whenComplete((entityManagerFactory, throwable) -> {
            if (entityManagerFactory != null) {
                log.debug("Completed initializing JPA EntityManagerFactory");
            } else {
                log.error("Could not initialize JPA EntityManagerFactory", throwable);
            }
        });
    }

    private <T> CompletionStage<T> withEntityManager(Function<EntityManager, T> block) {
        return factoryCompletionStage.thenCompose(factory -> executeInSlickContext(() -> {
            EntityManager entityManager = factory.createEntityManager();
            try {
                return block.apply(entityManager);
            } finally {
                entityManager.close();
            }
        }));
    }

    private <R> CompletionStage<R> executeInSlickContext(JFunction0<R> block) {
        return FutureConverters.toJava(slickDb.io(block));
    }

    private CompletionStage<Done> close() {
        return factoryCompletionStage.thenCompose(factory -> executeInSlickContext(() -> {
            log.debug("Closing JPA EntityManagerFactory");
            factory.close();
            return Done.getInstance();
        }));
    }
}
