/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import docs.home.persistence.BlogCommand.*;
import docs.home.persistence.BlogEvent.*;

// #full-example
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import java.util.Optional;
import akka.Done;

public class Post extends PersistentEntity<BlogCommand, BlogEvent, BlogState> {

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
      b.setCommandHandler(
          AddPost.class,
          (AddPost cmd, CommandContext<AddPostDone> ctx) -> {
            if (cmd.getContent().getTitle() == null || cmd.getContent().getTitle().equals("")) {
              ctx.invalidCommand("Title must be defined");
              return ctx.done();
            }

            final PostAdded postAdded = new PostAdded(entityId(), cmd.getContent());
            return ctx.thenPersist(
                postAdded,
                (PostAdded evt) ->
                    // After persist is done additional side effects can be performed
                    ctx.reply(new AddPostDone(entityId())));
          });

      // Event handlers are used both when persisting new events and when replaying
      // events.
      b.setEventHandlerChangingBehavior(
          PostAdded.class,
          evt -> becomePostAdded(new BlogState(Optional.of(evt.getContent()), false)));

      return b.build();
    }
  }

  // Behavior can be changed in the event handlers.
  private Behavior becomePostAdded(BlogState newState) {
    BehaviorBuilder b = newBehaviorBuilder(newState);

    b.setCommandHandler(
        ChangeBody.class,
        (cmd, ctx) ->
            ctx.thenPersist(
                new BodyChanged(entityId(), cmd.getBody()), evt -> ctx.reply(Done.getInstance())));

    b.setEventHandler(BodyChanged.class, evt -> state().withBody(evt.getBody()));

    return b.build();
  }
}
// #full-example
