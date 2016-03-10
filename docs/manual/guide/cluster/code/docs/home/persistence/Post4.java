package docs.home.persistence;

import akka.Done;

import javax.inject.Inject;
import com.lightbend.lagom.javadsl.pubsub.PubSubRef;
import com.lightbend.lagom.javadsl.pubsub.TopicId;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import java.util.Optional;

public class Post4 extends PersistentEntity<BlogCommand, BlogEvent, BlogState> {

  //#inject
  private final PubSubRef<PostPublished> publishedTopic;

  @Inject
  public Post4(PubSubRegistry pubSub) {
    publishedTopic = pubSub.refFor(TopicId.of(PostPublished.class, ""));
  }
  //#inject

  @Override
  public Behavior initialBehavior(Optional<BlogState> snapshotState) {
    if (snapshotState.isPresent() && !snapshotState.get().isEmpty()) {
      // behavior after snapshot must be restored by initialBehavior
      return becomePostAdded(snapshotState.get());
    } else {
      // Behavior consist of a State and defined event handlers and command handlers.
      BehaviorBuilder b = newBehaviorBuilder(BlogState.EMPTY);

      // Command handlers are invoked for incoming messages (commands).
      // A command handler must "return" the events to be persisted (if any).
      b.setCommandHandler(AddPost.class, (AddPost cmd, CommandContext<AddPostDone> ctx) -> {
        if (cmd.getContent().getTitle() == null || cmd.getContent().getTitle().equals("")) {
          ctx.invalidCommand("Title must be defined");
          return ctx.done();
        }

        final PostAdded postAdded =
            PostAdded.builder().content(cmd.getContent()).postId(entityId()).build();
        return ctx.thenPersist(postAdded, (PostAdded evt) ->
        // After persist is done additional side effects can be performed
            ctx.reply(AddPostDone.of(entityId())));
      });

      // Event handlers are used both when when persisting new events and when replaying
      // events.
      b.setEventHandlerChangingBehavior(PostAdded.class, evt ->
        becomePostAdded(state().withContent(Optional.of(evt.getContent()))));

      return b.build();
    }
  }

  // Behavior can be changed in the event handlers.
  private Behavior becomePostAdded(BlogState newState) {
    BehaviorBuilder b = newBehaviorBuilder(newState);

    b.setCommandHandler(ChangeBody.class,
        (cmd, ctx) -> ctx.thenPersist(BodyChanged.of(cmd.getBody()), evt ->
          ctx.reply(Done.getInstance())));

    b.setEventHandler(BodyChanged.class, evt -> state().withBody(evt.getBody()));

    //#publish
    b.setCommandHandler(Publish.class,
        (cmd, ctx) -> ctx.thenPersist(PostPublished.of(entityId()), evt -> {
            ctx.reply(Done.getInstance());
            publishedTopic.publish(evt);
          }));
    //#publish

    b.setEventHandler(PostPublished.class, evt ->
      BlogState.builder().from(state()).isPublished(true).build());

    return b.build();
  }

}
