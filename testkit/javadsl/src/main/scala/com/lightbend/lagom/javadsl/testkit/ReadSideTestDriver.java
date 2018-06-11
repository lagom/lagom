/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit;

import akka.Done;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.persistence.*;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler;
import org.pcollections.PSequence;
import play.inject.Injector;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 *
 */
@Singleton
public class ReadSideTestDriver implements ReadSide {

  private final Injector injector;
  private final Materializer materializer;

  private ConcurrentMap<Class<?>, List<Pair<ReadSideHandler<?>, Offset>>> processors = new ConcurrentHashMap<>();

  @Inject
  public ReadSideTestDriver(Injector injector, Materializer materializer) {
    this.injector = injector;
    this.materializer = materializer;
  }


  @Override
  public <Event extends AggregateEvent<Event>> void register(Class<? extends ReadSideProcessor<Event>> processorClass) {
    ReadSideProcessor<Event> processor = injector.instanceOf(processorClass);
    PSequence<AggregateEventTag<Event>> eventTags = processor.aggregateTags();

    CompletionStage<Done> processorInit = processor.buildHandler().globalPrepare().thenCompose(x -> {
      AggregateEventTag<Event> tag = eventTags.get(0);
      ReadSideHandler<Event> handler = processor.buildHandler();
      return handler.prepare(tag).thenApply(offset -> {
        List<Pair<ReadSideHandler<?>, Offset>> currentHandlers =
            processors.computeIfAbsent(tag.eventType(), (z) -> new ArrayList<>());
        currentHandlers.add(Pair.create(handler, offset));
        return Done.getInstance();
      });
    });

    try {
      processorInit.toCompletableFuture().get(30, SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("Couldn't register the processor on the testkit.", e);
    }

  }

  public <Event extends AggregateEvent<Event>> CompletionStage<Done> feed(Event e, Offset offset) {
    AggregateEventTagger<Event> tag = e.aggregateTag();

    List<Pair<ReadSideHandler<?>, Offset>> list = processors.get(tag.eventType());

    if (list == null) {
      throw new RuntimeException("No processor registered for Event " + tag.eventType().getCanonicalName());
    }

    List<CompletionStage<?>> stages = list.stream().map(pHandlerOffset -> {
      @SuppressWarnings("unchecked") ReadSideHandler<Event> handler = (ReadSideHandler<Event>) pHandlerOffset.first();
      Flow<Pair<Event, Offset>, Done, ?> flow = handler.handle();
          return Source.single(Pair.create(e, offset)).via(flow).runWith(Sink.ignore(), materializer);
        }
    ).collect(Collectors.toList());
    return doAll(stages);

  }

    // not public on purpose. See: https://github.com/lagom/lagom/issues/732
    /**
     * Returns a <code>CompletionStage&lt;Done&gt;</code> that completes when all the provided
     * <code>stages</code> complete. This abides to the
     * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html">failure rules</a>
     * described in the <code>CompletionStage</code> docs.
     */
  private CompletionStage<Done> doAll(List<CompletionStage<?>> stages) {
      CompletionStage<Done> result = CompletableFuture.completedFuture(Done.getInstance());
      for (CompletionStage<?> stage : stages) {
          result = result.thenCombine(stage, (d1, d2) -> Done.getInstance());
      }
      return result;
  }


}
