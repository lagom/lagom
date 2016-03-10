package docs.home.persistence;

//#processor1
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class BlogEventProcessor1 extends CassandraReadSideProcessor<BlogEvent> {

  @Override
  public AggregateEventTag<BlogEvent> aggregateTag() {
    // TODO return the tag for the events
    return null;
  }

  @Override
  public CompletionStage<Optional<UUID>> prepare(CassandraSession session) {
    // TODO prepare statements, fetch offset
    return noOffset();
  }

  @Override
  public EventHandlers defineEventHandlers(EventHandlersBuilder builder) {
    // TODO define event handlers
    return builder.build();
  }

}
//#processor1
