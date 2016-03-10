package docs.home.persistence;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

import akka.NotUsed;
import java.util.Arrays;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.BoundStatement;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSideProcessor;

public class BlogEventProcessor extends CassandraReadSideProcessor<BlogEvent> {

  //#tag
  @Override
  public AggregateEventTag<BlogEvent> aggregateTag() {
    return BlogEventTag.INSTANCE;
  }
  //#tag

  //#prepare-statements
  private PreparedStatement writeTitle = null; // initialized in prepare
  private PreparedStatement writeOffset = null; // initialized in prepare

  private void setWriteTitle(PreparedStatement writeTitle) {
    this.writeTitle = writeTitle;
  }

  private void setWriteOffset(PreparedStatement writeOffset) {
    this.writeOffset = writeOffset;
  }

  private CompletionStage<NotUsed> prepareWriteTitle(CassandraSession session) {
    return session.prepare("INSERT INTO blogsummary (partition, id, title) VALUES (1, ?, ?)")
        .thenApply(ps -> {
          setWriteTitle(ps);
          return NotUsed.getInstance();
        });
  }

  private CompletionStage<NotUsed> prepareWriteOffset(CassandraSession session) {
    return session.prepare("INSERT INTO blogevent_offset (partition, offset) VALUES (1, ?)")
        .thenApply(ps -> {
          setWriteOffset(ps);
          return NotUsed.getInstance();
        });
  }
  //#prepare-statements

  //#select-offset
  private CompletionStage<Optional<UUID>> selectOffset(CassandraSession session) {
    return session.selectOne("SELECT offset FROM blogevent_offset").thenApply(
        optionalRow -> optionalRow.map(r -> r.getUUID("offset")));
  }
  //#select-offset


  //#prepare
  @Override
  public CompletionStage<Optional<UUID>> prepare(CassandraSession session) {
    return
      prepareWriteTitle(session).thenCompose(a ->
      prepareWriteOffset(session).thenCompose(b ->
      selectOffset(session)));
  }
  //#prepare

  //#event-handlers
  @Override
  public EventHandlers defineEventHandlers(EventHandlersBuilder builder) {
    builder.setEventHandler(PostAdded.class, this::processPostAdded);
    return builder.build();
  }

  private CompletionStage<List<BoundStatement>> processPostAdded(PostAdded event, UUID offset) {
    BoundStatement bindWriteTitle = writeTitle.bind();
    bindWriteTitle.setString("id", event.getPostId());
    bindWriteTitle.setString("title", event.getContent().getTitle());
    BoundStatement bindWriteTitleOffset = writeOffset.bind(offset);
    return completedStatements(Arrays.asList(bindWriteTitle, bindWriteTitleOffset));
  }
  //#event-handlers



}
