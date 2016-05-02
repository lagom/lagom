package docs.home.persistence;

import scala.PartialFunction;

import java.util.Optional;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;

import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;

public class BlogServiceImpl3 implements BlogService2 {

  private final PersistentEntityRegistry persistentEntityRegistry;
  private final CassandraSession cassandraSession;

  //#register-event-processor
  @Inject
  public BlogServiceImpl3(
      PersistentEntityRegistry persistentEntityRegistry,
      CassandraSession cassandraSession,
      CassandraReadSide cassandraReadSide) {

    this.persistentEntityRegistry = persistentEntityRegistry;
    this.cassandraSession = cassandraSession;

    cassandraReadSide.register(BlogEventProcessor.class);
  }
  //#register-event-processor

  @Override
  public ServiceCall<NotUsed, Source<PostSummary, ?>> getPostSummaries() {
    return request -> {
      Source<PostSummary, ?> summaries = cassandraSession.select(
          "SELECT id, title FROM postsummary;").map(row ->
            PostSummary.of(row.getString("id"), row.getString("title")));
      return CompletableFuture.completedFuture(summaries);
    };
  }

  //#event-stream
  public ServiceCall<NotUsed, Source<PostSummary, ?>> newPosts() {
    final PartialFunction<BlogEvent, PostSummary> collectFunction =
        new PFBuilder<BlogEvent, PostSummary>()
        .match(PostAdded.class, evt ->
           PostSummary.of(evt.getPostId(), evt.getContent().getTitle()))
        .build();

    return request -> {
      Source<PostSummary, ?> stream = persistentEntityRegistry
        .eventStream(BlogEventTag.INSTANCE, Optional.empty())
          .map(pair -> pair.first()).collect(collectFunction);
      return CompletableFuture.completedFuture(stream);
    };
  }
  //#event-stream

}
