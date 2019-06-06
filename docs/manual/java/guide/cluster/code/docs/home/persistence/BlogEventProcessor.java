/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import akka.Done;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;

import java.util.concurrent.CompletionStage;
import org.pcollections.PSequence;

public class BlogEventProcessor extends ReadSideProcessor<BlogEvent> {

  // #my-database
  public interface MyDatabase {
    /** Create the tables needed for this read side if not already created. */
    CompletionStage<Done> createTables();

    /** Load the offset of the last event processed. */
    CompletionStage<Offset> loadOffset(AggregateEventTag<BlogEvent> tag);

    /** Handle the post added event. */
    CompletionStage<Done> handleEvent(BlogEvent event, Offset offset);
  }
  // #my-database

  private final MyDatabase myDatabase;

  public BlogEventProcessor(MyDatabase myDatabase) {
    this.myDatabase = myDatabase;
  }

  // #tag
  @Override
  public PSequence<AggregateEventTag<BlogEvent>> aggregateTags() {
    return BlogEvent.TAG.allTags();
  }
  // #tag

  // #build-handler
  @Override
  public ReadSideHandler<BlogEvent> buildHandler() {

    return new ReadSideHandler<BlogEvent>() {

      @Override
      public CompletionStage<Done> globalPrepare() {
        return myDatabase.createTables();
      }

      @Override
      public CompletionStage<Offset> prepare(AggregateEventTag<BlogEvent> tag) {
        return myDatabase.loadOffset(tag);
      }

      @Override
      public Flow<Pair<BlogEvent, Offset>, Done, ?> handle() {
        return Flow.<Pair<BlogEvent, Offset>>create()
            .mapAsync(
                1,
                eventAndOffset ->
                    myDatabase.handleEvent(eventAndOffset.first(), eventAndOffset.second()));
      }
    };
  }
  // #build-handler

}
