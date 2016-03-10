package sample.chirper.friend.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import akka.Done;
import sample.chirper.friend.api.User;
import sample.chirper.friend.impl.FriendCommand.AddFriend;
import sample.chirper.friend.impl.FriendCommand.CreateUser;
import sample.chirper.friend.impl.FriendCommand.GetUser;
import sample.chirper.friend.impl.FriendCommand.GetUserReply;
import sample.chirper.friend.impl.FriendEvent.FriendAdded;
import sample.chirper.friend.impl.FriendEvent.UserCreated;

public class FriendEntity extends PersistentEntity<FriendCommand, FriendEvent, FriendState> {

  @Override
  public Behavior initialBehavior(Optional<FriendState> snapshotState) {

    BehaviorBuilder b = newBehaviorBuilder(snapshotState.orElse(
      new FriendState(Optional.empty())));

    b.setCommandHandler(CreateUser.class, (cmd, ctx) -> {
      if (state().user.isPresent()) {
        ctx.invalidCommand("User " + entityId() + " is already created");
        return ctx.done();
      } else {
        User user = cmd.user;
        List<FriendEvent> events = new ArrayList<FriendEvent>();
        events.add(new UserCreated(user.userId, user.name));
        for (String friendId : user.friends) {
          events.add(new FriendAdded(user.userId, friendId));
        }
        return ctx.thenPersistAll(events, () -> ctx.reply(Done.getInstance()));
      }
    });

    b.setEventHandler(UserCreated.class,
        evt -> new FriendState(Optional.of(new User(evt.userId, evt.name))));

    b.setCommandHandler(AddFriend.class, (cmd, ctx) -> {
      if (!state().user.isPresent()) {
        ctx.invalidCommand("User " + entityId() + " is not  created");
        return ctx.done();
      } else if (state().user.get().friends.contains(cmd.friendUserId)) {
        ctx.reply(Done.getInstance());
        return ctx.done();
      } else {
        return ctx.thenPersist(new FriendAdded(getUserId(), cmd.friendUserId), evt ->
          ctx.reply(Done.getInstance()));
      }
    });

    b.setEventHandler(FriendAdded.class, evt -> state().addFriend(evt.friendId));

    b.setReadOnlyCommandHandler(GetUser.class, (cmd, ctx) -> {
      ctx.reply(new GetUserReply(state().user));
    });

    return b.build();
  }

  private String getUserId() {
    return state().user.get().userId;
  }
}
