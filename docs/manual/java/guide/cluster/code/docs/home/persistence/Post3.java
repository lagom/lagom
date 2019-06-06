/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import docs.home.persistence.BlogCommand.*;
import docs.home.persistence.BlogEvent.*;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import java.util.Optional;
import akka.Done;

public class Post3 extends PersistentEntity<BlogCommand, BlogEvent, BlogState> {

  // #snapshot
  @Override
  public Behavior initialBehavior(Optional<BlogState> snapshotState) {
    if (snapshotState.isPresent() && !snapshotState.get().isEmpty()) {
      // behavior after snapshot must be restored by initialBehavior
      // if we have a non-empty BlogState we know that the initial
      // AddPost has been performed
      return becomePostAdded(snapshotState.get());
    } else {
      // behavior when no snapshot is used
      BehaviorBuilder b = newBehaviorBuilder(BlogState.EMPTY);

      // TODO define command and event handlers

      return b.build();
    }
  }
  // #snapshot

  private Behavior becomePostAdded(BlogState newState) {
    BehaviorBuilder b = newBehaviorBuilder(newState);

    // #read-only-command-handler
    b.setReadOnlyCommandHandler(GetPost.class, (cmd, ctx) -> ctx.reply(state().getContent().get()));
    // #read-only-command-handler

    // #reply
    b.setCommandHandler(
        ChangeBody.class,
        (cmd, ctx) ->
            ctx.thenPersist(
                new BodyChanged(entityId(), cmd.getBody()), evt -> ctx.reply(Done.getInstance())));
    // #reply

    b.setEventHandler(BodyChanged.class, evt -> state().withBody(evt.getBody()));

    return b.build();
  }
}
// #post1
