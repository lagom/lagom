/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #imports
import akka.Done;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import org.pcollections.PSequence;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide.*;
// #imports

public interface CassandraBlogEventProcessor {

  interface Initial {
    // #initial
    public class BlogEventProcessor extends ReadSideProcessor<BlogEvent> {

      private final CassandraSession session;
      private final CassandraReadSide readSide;

      @Inject
      public BlogEventProcessor(CassandraSession session, CassandraReadSide readSide) {
        this.session = session;
        this.readSide = readSide;
      }

      @Override
      public ReadSideProcessor.ReadSideHandler<BlogEvent> buildHandler() {
        // TODO build read side handler
        return null;
      }

      @Override
      public PSequence<AggregateEventTag<BlogEvent>> aggregateTags() {
        // TODO return the tag for the events
        return null;
      }
    }
    // #initial
  }

  public class BlogEventProcessor extends ReadSideProcessor<BlogEvent> {

    private final CassandraSession session;
    private final CassandraReadSide readSide;

    @Inject
    public BlogEventProcessor(CassandraSession session, CassandraReadSide readSide) {
      this.session = session;
      this.readSide = readSide;
    }

    // #tag
    @Override
    public PSequence<AggregateEventTag<BlogEvent>> aggregateTags() {
      return BlogEvent.TAG.allTags();
    }
    // #tag

    // #create-table
    private CompletionStage<Done> createTable() {
      return session.executeCreateTable(
          "CREATE TABLE IF NOT EXISTS blogsummary ( " + "id TEXT, title TEXT, PRIMARY KEY (id))");
    }
    // #create-table

    // #prepare-statements
    private PreparedStatement writeTitle = null; // initialized in prepare

    private CompletionStage<Done> prepareWriteTitle() {
      return session
          .prepare("INSERT INTO blogsummary (id, title) VALUES (?, ?)")
          .thenApply(
              ps -> {
                this.writeTitle = ps;
                return Done.getInstance();
              });
    }
    // #prepare-statements

    // #post-added
    private CompletionStage<List<BoundStatement>> processPostAdded(BlogEvent.PostAdded event) {
      BoundStatement bindWriteTitle = writeTitle.bind();
      bindWriteTitle.setString("id", event.getPostId());
      bindWriteTitle.setString("title", event.getContent().getTitle());
      return completedStatements(Arrays.asList(bindWriteTitle));
    }
    // #post-added

    @Override
    public ReadSideHandler<BlogEvent> buildHandler() {
      // #create-builder
      CassandraReadSide.ReadSideHandlerBuilder<BlogEvent> builder =
          readSide.builder("blogsummaryoffset");
      // #create-builder

      // #register-global-prepare
      builder.setGlobalPrepare(this::createTable);
      // #register-global-prepare

      // #register-prepare
      builder.setPrepare(tag -> prepareWriteTitle());
      // #register-prepare

      // #set-event-handler
      builder.setEventHandler(BlogEvent.PostAdded.class, this::processPostAdded);
      // #set-event-handler

      // #build
      return builder.build();
      // #build
    }
  }
}
