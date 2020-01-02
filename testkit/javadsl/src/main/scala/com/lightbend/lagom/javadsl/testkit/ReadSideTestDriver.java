/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
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

/**
 *
 */
@Singleton
public class ReadSideTestDriver implements ReadSide {

  private final Injector injector;
  private final Materializer materializer;

  private ConcurrentMap<Class<?>, List<CompletionStage<Pair<ReadSideHandler<?>, Offset>>>> processors = new ConcurrentHashMap<>();

  @Inject
  public ReadSideTestDriver(Injector injector, Materializer materializer) {
    this.injector = injector;
    this.materializer = materializer;
  }


  @Override
  public <Event extends AggregateEvent<Event>> void register(Class<? extends ReadSideProcessor<Event>> processorClass) {

    ReadSideProcessor<Event> processor = injector.instanceOf(processorClass);
    PSequence<AggregateEventTag<Event>> eventTags = processor.aggregateTags();
    AggregateEventTag<Event> tag = eventTags.get(0);

    ReadSideHandler<Event> handler = processor.buildHandler();


    CompletionStage<Pair<ReadSideHandler<?>, Offset>> handlerFuture =
      handler.globalPrepare()
        .thenCompose(x -> handler.prepare(tag))
        .thenApply(offset -> Pair.create(handler, offset));

      List<CompletionStage<Pair<ReadSideHandler<?>, Offset>>> currentHandlers =
          processors.computeIfAbsent(tag.eventType(), (z) -> new ArrayList<>());

      currentHandlers.add(handlerFuture);
  }

  public <Event extends AggregateEvent<Event>> CompletionStage<Done> feed(Event e, Offset offset) {

    AggregateEventTagger<Event> tag = e.aggregateTag();
    List<CompletionStage<Pair<ReadSideHandler<?>, Offset>>> list = processors.get(tag.eventType());

    if (list == null) {
      throw new RuntimeException("No processor registered for Event " + tag.eventType().getCanonicalName());
    }

    List<CompletionStage<Done>> stages = list.stream().map(handlerOffsetStage -> {
      return handlerOffsetStage.thenCompose(handlerOffset -> {
        @SuppressWarnings("unchecked") ReadSideHandler<Event> handler = (ReadSideHandler<Event>) handlerOffset.first();
        Flow<Pair<Event, Offset>, Done, ?> flow = handler.handle();
        return Source.single(Pair.create(e, offset)).via(flow).runWith(Sink.ignore(), materializer);
      });
    }).collect(Collectors.toList());

    return doAll(stages);

  }

    // not public on purpose. See: https://github.com/lagom/lagom/issues/732
    /**
     * Returns a <code>CompletionStage&lt;Done&gt;</code> that completes when all the provided
     * <code>stages</code> complete. This abides to the
     * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html">failure rules</a>
     * described in the <code>CompletionStage</code> docs.
     */
  private CompletionStage<Done> doAll(List<CompletionStage<Done>> stages) {
      CompletionStage<Done> result = CompletableFuture.completedFuture(Done.getInstance());
      for (CompletionStage<?> stage : stages) {
          result = result.thenCombine(stage, (d1, d2) -> Done.getInstance());
      }
      return result;
  }


}
