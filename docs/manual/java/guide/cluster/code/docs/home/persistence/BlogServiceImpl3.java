/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import scala.PartialFunction;

import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;

import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;

public interface BlogServiceImpl3 {
  public class BlogServiceImpl {

    private final PersistentEntityRegistry persistentEntityRegistry;

    // #register-event-processor
    @Inject
    public BlogServiceImpl(PersistentEntityRegistry persistentEntityRegistry, ReadSide readSide) {
      this.persistentEntityRegistry = persistentEntityRegistry;

      readSide.register(BlogEventProcessor.class);
    }
    // #register-event-processor

    // #event-stream
    public ServiceCall<NotUsed, Source<PostSummary, ?>> newPosts() {
      final PartialFunction<BlogEvent, PostSummary> collectFunction =
          new PFBuilder<BlogEvent, PostSummary>()
              .match(
                  BlogEvent.PostAdded.class,
                  evt -> new PostSummary(evt.getPostId(), evt.getContent().getTitle()))
              .build();

      return request -> {
        Source<PostSummary, ?> stream =
            persistentEntityRegistry
                .eventStream(BlogEvent.TAG.forEntityId(""), Offset.NONE)
                .map(pair -> pair.first())
                .collect(collectFunction);
        return CompletableFuture.completedFuture(stream);
      };
    }
    // #event-stream
  }
}
