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
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;
import slick.jdbc.JdbcBackend;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Singleton
public class JpaSessionImpl implements JpaSession {
    // TODO: Make these constants configuration parameters in the Lagom framework implementation
    private static final FiniteDuration INIT_TIMEOUT = FiniteDuration.create(1, TimeUnit.MINUTES);
    private static final FiniteDuration INIT_RETRY_INTERVAL = FiniteDuration.create(5, TimeUnit.SECONDS);
    private static final String PERSISTENCE_UNIT_NAME = "default";

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ActorSystem actorSystem;
    private final JdbcBackend.DatabaseDef slick;
    private final CompletionStage<EntityManagerFactory> factoryCompletionStage;

    @Inject
    public JpaSessionImpl(ActorSystem actorSystem, SlickProvider slick, ApplicationLifecycle lifecycle) {
        this.slick = slick.db();
        this.actorSystem = actorSystem;
        log.debug("Initializing JPA EntityManagerFactory with persistence unit name {}", PERSISTENCE_UNIT_NAME);
        Deadline deadline = INIT_TIMEOUT.fromNow();
        this.factoryCompletionStage =
                tryCreateEntityManagerFactory(deadline, INIT_RETRY_INTERVAL)
                        .whenComplete((entityManagerFactory, throwable) -> {
                            if (entityManagerFactory != null) {
                                log.debug("Completed initializing JPA EntityManagerFactory");
                            } else {
                                log.error("Could not initialize JPA EntityManagerFactory", throwable);
                            }
                        });
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

    private CompletionStage<EntityManagerFactory> tryCreateEntityManagerFactory(Deadline deadline,
                                                                                FiniteDuration retryInterval) {
        CompletionStage<EntityManagerFactory> result = executeInSlickContext(() ->
                Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME)
        );
        // This wrapping and then composing with identity logic is because
        // CompletionStage has no equivalent to Scala's Future.recoverWith
        return result.thenApply(CompletableFuture::completedFuture)
                .exceptionally(e -> retryCreateEntityManagerFactory(e, deadline, retryInterval))
                .thenCompose(Function.identity());
    }

    private CompletableFuture<EntityManagerFactory> retryCreateEntityManagerFactory(Throwable exception,
                                                                                    Deadline deadline,
                                                                                    FiniteDuration retryInterval) {
        log.warn("Exception while initializing JPA EntityManagerFactory", exception);
        CompletableFuture<EntityManagerFactory> future = new CompletableFuture<>();
        if (deadline.timeLeft().lteq(retryInterval)) {
            // Duration is up, don't try and recover from failure
            future.completeExceptionally(exception);
        } else {
            log.info("Will retry initializing JPA EntityManagerFactory in {}", retryInterval);
            actorSystem.scheduler().scheduleOnce(
                    retryInterval,
                    () -> tryCreateEntityManagerFactory(deadline, retryInterval)
                            .thenAccept(future::complete)
                            .exceptionally(ex -> {
                                future.completeExceptionally(ex);
                                return null;
                            }),
                    actorSystem.dispatcher()
            );
        }
        return future;
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
        return FutureConverters.toJava(slick.io(block));
    }

    private CompletionStage<Done> close() {
        return factoryCompletionStage.thenCompose(factory -> executeInSlickContext(() -> {
            log.debug("Closing JPA EntityManagerFactory");
            factory.close();
            return Done.getInstance();
        }));
    }
}
