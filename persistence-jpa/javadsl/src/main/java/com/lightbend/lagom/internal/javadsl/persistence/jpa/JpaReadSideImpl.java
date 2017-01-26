/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa;

import akka.Done;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter;
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetDao;
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetStore;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.compat.java8.FutureConverters;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static scala.collection.JavaConversions.asJavaIterable;

@Singleton
public class JpaReadSideImpl implements JpaReadSide {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final JpaSession jpa;
    private final SlickOffsetStore offsetStore;

    @Inject
    public JpaReadSideImpl(JpaSession jpa, SlickOffsetStore offsetStore) {
        this.jpa = jpa;
        this.offsetStore = offsetStore;
    }

    @Override
    public <Event extends AggregateEvent<Event>> ReadSideHandlerBuilder<Event> builder(String readSideId) {
        return new ReadSideHandlerBuilder<Event>() {
            private Consumer<EntityManager> globalPrepare = entityManager -> {};
            private BiConsumer<EntityManager, AggregateEventTag<Event>> prepare = (entityManager, tag) -> {};
            private PMap<Class<? extends Event>, BiConsumer<EntityManager, ? extends Event>> eventHandlers =
                    HashTreePMap.empty();

            @Override
            public ReadSideHandlerBuilder<Event> setGlobalPrepare(Consumer<EntityManager> callback) {
                globalPrepare = callback;
                return this;
            }

            @Override
            public ReadSideHandlerBuilder<Event> setPrepare(BiConsumer<EntityManager, AggregateEventTag<Event>> callback) {
                prepare = callback;
                return this;
            }

            @Override
            public <E extends Event> ReadSideHandlerBuilder<Event> setEventHandler(Class<E> eventClass, BiConsumer<EntityManager, E> handler) {
                eventHandlers = eventHandlers.plus(eventClass, handler);
                return this;
            }

            @Override
            public ReadSideHandler<Event> build() {
                return new JpaReadSideHandler<>(readSideId, globalPrepare, prepare, eventHandlers);
            }
        };
    }

    private class JpaReadSideHandler<Event extends AggregateEvent<Event>> extends ReadSideHandler<Event> {
        private final String readSideId;
        private final Consumer<EntityManager> globalPrepare;
        private final BiConsumer<EntityManager, AggregateEventTag<Event>> prepare;
        private final PMap<Class<? extends Event>, BiConsumer<EntityManager, ? extends Event>> eventHandlers;

        private volatile AggregateEventTag<Event> tag;
        private volatile SlickOffsetDao offsetDao;

        private JpaReadSideHandler(
                String readSideId,
                Consumer<EntityManager> globalPrepare,
                BiConsumer<EntityManager, AggregateEventTag<Event>> prepare,
                PMap<Class<? extends Event>, BiConsumer<EntityManager, ? extends Event>> eventHandlers) {
            this.readSideId = readSideId;
            this.globalPrepare = globalPrepare;
            this.prepare = prepare;
            this.eventHandlers = eventHandlers;
        }

        @Override
        public CompletionStage<Done> globalPrepare() {
            return jpa.withTransaction(entityManager -> {
                log.debug("Starting globalPrepare in JpaReadSideHandler: {}", readSideId);
                globalPrepare.accept(entityManager);
                log.debug("Completed globalPrepare in JpaReadSideHandler: {}", readSideId);
                return Done.getInstance();
            });
        }

        @Override
        public CompletionStage<Offset> prepare(AggregateEventTag<Event> tag) {
            this.tag = tag;
            return jpa.withTransaction(entityManager -> {
                log.debug("Starting prepare tag {} in JpaReadSideHandler: {}", tag, readSideId);
                prepare.accept(entityManager, tag);
                log.debug("Completed prepare tag {} in JpaReadSideHandler: {}", tag, readSideId);
                return Done.getInstance();
            }).thenCombine(prepareOffsetDao(tag), (done, offset) -> {
                log.debug("Starting events for tag {} from offset {} in JpaReadSideHandler: {}",
                        tag, offset, readSideId);
                return offset;
            });
        }

        @Override
        public Flow<Pair<Event, Offset>, Done, ?> handle() {
            return Flow.<Pair<Event, Offset>>create().mapAsync(1, eventAndOffset -> {
                Event event = eventAndOffset.first();
                Offset offset = eventAndOffset.second();
                @SuppressWarnings("unchecked") Class<Event> eventClass = (Class<Event>) event.getClass();
                @SuppressWarnings("unchecked") BiConsumer<EntityManager, Event> eventHandler =
                        (BiConsumer<EntityManager, Event>) eventHandlers.get(eventClass);
                if (eventHandler != null) {
                    return jpa.withTransaction(entityManager -> {
                        if (log.isDebugEnabled())
                            log.debug("Starting handler for event {} at offset {} in JpaReadSideHandler: {}",
                                    eventClass.getName(), offset, readSideId);
                        eventHandler.accept(entityManager, event);
                        updateOffset(entityManager, offset);
                        if (log.isDebugEnabled())
                            log.debug("Completed handler for event {} at offset {} in JpaReadSideHandler: {}",
                                    eventClass.getName(), offset, readSideId);
                        return Done.getInstance();
                    });
                } else {
                    if (log.isDebugEnabled())
                        log.debug("Unhandled event {} at offset {} in JpaReadSideHandler: {}",
                                eventClass.getName(), offset, readSideId);
                    return CompletableFuture.completedFuture(Done.getInstance());
                }
            });
        }

        private CompletionStage<Offset> prepareOffsetDao(AggregateEventTag<Event> tag) {
            return FutureConverters.toJava(offsetStore.prepare(readSideId, tag.tag()))
                    .thenApply(offsetDao -> {
                        this.offsetDao = offsetDao;
                        return OffsetAdapter.offsetToDslOffset(offsetDao.loadedOffset());
                    });
        }

        private void updateOffset(EntityManager entityManager, Offset offset) {
            Long sequenceOffset = offset instanceof Offset.Sequence ?
                    ((Offset.Sequence) offset).value() : null;
            String timeUuidOffset = offset instanceof Offset.TimeBasedUUID ?
                    ((Offset.TimeBasedUUID) offset).value().toString() : null;
            Iterable<String> sqlStatements =
                    asJavaIterable(offsetDao.updateOffsetQuery(OffsetAdapter.dslOffsetToOffset(offset)).statements());

            for (String statement : sqlStatements) {
                // NOTE: The order of parameters here depends on the order chosen
                // by Slick, based on the table definition in JdbcOffsetStore.
                entityManager.createNativeQuery(statement)
                        .setParameter(1, readSideId)
                        .setParameter(2, tag.tag())
                        .setParameter(3, sequenceOffset)
                        .setParameter(4, timeUuidOffset)
                        .executeUpdate();
            }
        }
    }
}
