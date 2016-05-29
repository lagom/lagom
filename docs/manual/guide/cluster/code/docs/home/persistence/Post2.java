package docs.home.persistence;

import java.util.Optional;
import akka.Done;

//#post1
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

public class Post2 extends PersistentEntity<BlogCommand, BlogEvent, BlogState> {

  @Override
  public Behavior initialBehavior(Optional<BlogState> snapshotState) {
    BehaviorBuilder b = newBehaviorBuilder(
        snapshotState.orElse(BlogState.EMPTY));

    //#command-handler
    // Command handlers are invoked for incoming messages (commands).
    // A command handler must "return" the events to be persisted (if any).
    b.setCommandHandler(AddPost.class, (AddPost cmd, CommandContext<AddPostDone> ctx) -> {
      final PostAdded postAdded =
          PostAdded.builder().content(cmd.getContent()).postId(entityId()).build();
      return ctx.thenPersist(postAdded, (PostAdded evt) ->
        // After persist is done additional side effects can be performed
        ctx.reply(AddPostDone.of(entityId())));
    });
    //#command-handler

    //#validate-command
    b.setCommandHandler(AddPost.class, (AddPost cmd, CommandContext<AddPostDone> ctx) -> {
      if (cmd.getContent().getTitle() == null || cmd.getContent().getTitle().equals("")) {
        ctx.invalidCommand("Title must be defined");
        return ctx.done();
      }
      //#validate-command

      final PostAdded postAdded =
          PostAdded.builder().content(cmd.getContent()).postId(entityId()).build();
      return ctx.thenPersist(postAdded, (PostAdded evt) ->
      // After persist is done additional side effects can be performed
          ctx.reply(AddPostDone.of(entityId())));
    });

    //#event-handler
    // Event handlers are used both when persisting new events
    // and when replaying events.
    b.setEventHandler(PostAdded.class, evt ->
      state().withContent(Optional.of(evt.getContent())));
    //#event-handler

    //#change-behavior
    b.setEventHandlerChangingBehavior(PostAdded.class, evt ->
      becomePostAdded(state().withContent(Optional.of(evt.getContent()))));
    //#change-behavior

    return b.build();
  }

  //#change-behavior-become
  private Behavior becomePostAdded(BlogState newState) {
    BehaviorBuilder b = newBehaviorBuilder(newState);

    b.setReadOnlyCommandHandler(GetPost.class, (cmd, ctx) ->
      ctx.reply(state().getContent().get()));

    b.setCommandHandler(ChangeBody.class,
        (cmd, ctx) -> ctx.thenPersist(BodyChanged.of(cmd.getBody()), evt ->
          ctx.reply(Done.getInstance())));

    b.setEventHandler(BodyChanged.class, evt -> state().withBody(evt.getBody()));

    return b.build();
  }
  //#change-behavior-become

}
