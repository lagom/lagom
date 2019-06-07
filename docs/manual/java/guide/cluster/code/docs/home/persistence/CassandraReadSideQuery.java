/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #imports
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import akka.stream.javadsl.Source;
// #imports

public interface CassandraReadSideQuery {

  interface BlogService {
    ServiceCall<NotUsed, Source<PostSummary, ?>> getPostSummaries();
  }

  // #service-impl
  public class BlogServiceImpl implements BlogService {

    private final CassandraSession cassandraSession;

    @Inject
    public BlogServiceImpl(CassandraSession cassandraSession) {
      this.cassandraSession = cassandraSession;
    }

    @Override
    public ServiceCall<NotUsed, Source<PostSummary, ?>> getPostSummaries() {
      return request -> {
        Source<PostSummary, ?> summaries =
            cassandraSession
                .select("SELECT id, title FROM blogsummary;")
                .map(row -> new PostSummary(row.getString("id"), row.getString("title")));
        return CompletableFuture.completedFuture(summaries);
      };
    }
  }
  // #service-impl

}
