/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa;

import akka.Done;
import akka.actor.ActorSystem;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.SlickProvider;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.inject.ApplicationLifecycle;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.JFunction0;
import scala.concurrent.duration.FiniteDuration;
import slick.jdbc.JdbcBackend;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static play.utils.Threads.withContextClassLoader;

@Singleton
class JpaSessionImpl implements JpaSession {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    private final String persistenceUnitName;
    private final FiniteDuration initRetryIntervalMin;
    private final double initRetryIntervalFactor;
    private final int initRetryMax;

    private final JdbcBackend.DatabaseDef slickDb;
    private final CompletionStage<EntityManagerFactory> factoryCompletionStage;

    @Inject
    public JpaSessionImpl(Configuration config, SlickProvider slick, ActorSystem actorSystem, ApplicationLifecycle lifecycle) {
        Config jpaConfig = config.underlying().getConfig("lagom.persistence.jpa");
        this.persistenceUnitName = jpaConfig.getString("persistence-unit");
        this.initRetryIntervalMin = toFiniteDuration(jpaConfig.getDuration("initialization-retry.interval.min"));
        this.initRetryIntervalFactor = jpaConfig.getDouble("initialization-retry.interval.factor");
        this.initRetryMax = jpaConfig.getInt("initialization-retry.max-retries");

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

    private static FiniteDuration toFiniteDuration(Duration duration) {
        return FiniteDuration.fromNanos(duration.toNanos());
    }

    private CompletionStage<EntityManagerFactory> createEntityManagerFactory(ActorSystem actorSystem) {
        log.debug("Initializing JPA EntityManagerFactory with persistence unit name {}", persistenceUnitName);
        Retry jpaInitializer = new Retry(initRetryIntervalMin, initRetryIntervalFactor, initRetryMax) {
            @Override
            public void onRetry(Throwable throwable, FiniteDuration delay, int remainingRetries) {
                log.warn("Exception while initializing JPA EntityManagerFactory", throwable);
                log.info("Will retry initializing JPA EntityManagerFactory {} times in {}", remainingRetries, delay);
            }
        };
        return jpaInitializer.retry(
                () -> Persistence.createEntityManagerFactory(persistenceUnitName),
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
        return FutureConverters.toJava(slickDb.io(wrapWithContextClassLoader(block)));
    }

    private <R> JFunction0<R> wrapWithContextClassLoader(JFunction0<R> block) {
        return () -> withContextClassLoader(classLoader, block);
    }

    private CompletionStage<Done> close() {
        return factoryCompletionStage.thenCompose(factory -> executeInSlickContext(() -> {
            log.debug("Closing JPA EntityManagerFactory");
            factory.close();
            return Done.getInstance();
        }));
    }
}
