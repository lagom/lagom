package docs.home.persistence;

//#service-impl
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import akka.stream.javadsl.Source;

public class BlogServiceImpl2 implements BlogService2 {

  private final CassandraSession cassandraSession;

  @Inject
  public BlogServiceImpl2(CassandraSession cassandraSession) {
    this.cassandraSession = cassandraSession;
  }

  @Override
  public ServiceCall<NotUsed, Source<PostSummary, ?>> getPostSummaries() {
    return request -> {
      Source<PostSummary, ?> summaries = cassandraSession.select(
          "SELECT id, title FROM postsummary;").map(row ->
            PostSummary.of(row.getString("id"), row.getString("title")));
      return CompletableFuture.completedFuture(summaries);
    };
  }
}
//#service-impl
