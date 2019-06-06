/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #imports
import com.google.common.collect.ImmutableMap;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import org.pcollections.PSequence;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;
// #imports

public interface JpaBlogEventProcessor {

  interface Initial {
    // #initial
    public class BlogEventProcessor extends ReadSideProcessor<BlogEvent> {

      private final JpaReadSide readSide;

      @Inject
      public BlogEventProcessor(JpaReadSide readSide) {
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

    private final JpaReadSide readSide;

    @Inject
    public BlogEventProcessor(JpaReadSide readSide) {
      this.readSide = readSide;
    }

    // #tag
    @Override
    public PSequence<AggregateEventTag<BlogEvent>> aggregateTags() {
      return BlogEvent.TAG.allTags();
    }
    // #tag

    // #create-schema
    private void createSchema(@SuppressWarnings("unused") EntityManager ignored) {
      Persistence.generateSchema("default", ImmutableMap.of("hibernate.hbm2ddl.auto", "update"));
    }
    // #create-schema

    // #post-added
    private void processPostAdded(EntityManager entityManager, BlogEvent.PostAdded event) {
      BlogSummaryJpaEntity summary = new BlogSummaryJpaEntity();
      summary.setId(event.getPostId());
      summary.setTitle(event.getContent().getTitle());
      entityManager.persist(summary);
    }
    // #post-added

    @Override
    public ReadSideHandler<BlogEvent> buildHandler() {
      // #create-builder
      JpaReadSide.ReadSideHandlerBuilder<BlogEvent> builder = readSide.builder("blogsummaryoffset");
      // #create-builder

      // #register-global-prepare
      builder.setGlobalPrepare(this::createSchema);
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
