/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #imports
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcReadSide;
import org.pcollections.PSequence;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
// #imports

public interface JdbcBlogEventProcessor {

  interface Initial {
    // #initial
    public class BlogEventProcessor extends ReadSideProcessor<BlogEvent> {

      private final JdbcReadSide readSide;

      @Inject
      public BlogEventProcessor(JdbcReadSide readSide) {
        this.readSide = readSide;
      }

      @Override
      public ReadSideHandler<BlogEvent> buildHandler() {
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

    private final JdbcReadSide readSide;

    @Inject
    public BlogEventProcessor(JdbcReadSide readSide) {
      this.readSide = readSide;
    }

    // #tag
    @Override
    public PSequence<AggregateEventTag<BlogEvent>> aggregateTags() {
      return BlogEvent.TAG.allTags();
    }
    // #tag

    // #create-table
    private void createTable(Connection connection) throws SQLException {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "CREATE TABLE IF NOT EXISTS blogsummary ( "
                  + "id VARCHAR(64), title VARCHAR(256), PRIMARY KEY (id))")) {
        ps.execute();
      }
    }
    // #create-table

    // #post-added
    private void processPostAdded(Connection connection, BlogEvent.PostAdded event)
        throws SQLException {
      PreparedStatement statement =
          connection.prepareStatement("INSERT INTO blogsummary (id, title) VALUES (?, ?)");
      statement.setString(1, event.getPostId());
      statement.setString(2, event.getContent().getTitle());
      statement.execute();
    }
    // #post-added

    @Override
    public ReadSideHandler<BlogEvent> buildHandler() {
      // #create-builder
      JdbcReadSide.ReadSideHandlerBuilder<BlogEvent> builder =
          readSide.builder("blogsummaryoffset");
      // #create-builder

      // #register-global-prepare
      builder.setGlobalPrepare(this::createTable);
      // #register-global-prepare

      // #set-event-handler
      builder.setEventHandler(BlogEvent.PostAdded.class, this::processPostAdded);
      // #set-event-handler

      // #build
      return builder.build();
      // #build
    }
  }
}
