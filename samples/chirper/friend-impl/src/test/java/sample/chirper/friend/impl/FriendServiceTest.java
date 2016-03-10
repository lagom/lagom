/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.chirper.friend.impl;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;
import sample.chirper.friend.api.FriendId;
import sample.chirper.friend.api.FriendService;
import sample.chirper.friend.api.User;
import scala.concurrent.duration.FiniteDuration;

import akka.NotUsed;

public class FriendServiceTest {

  @Test
  public void shouldBeAbleToCreateUsersAndConnectFriends() throws Exception {
    withServer(defaultSetup(), server -> {
      FriendService friendService = server.client(FriendService.class);
      User usr1 = new User("usr1", "User 1");
      friendService.createUser().invoke(usr1).toCompletableFuture().get(10, SECONDS);
      User usr2 = new User("usr2", "User 2");
      friendService.createUser().invoke(usr2).toCompletableFuture().get(3, SECONDS);
      User usr3 = new User("usr3", "User 3");
      friendService.createUser().invoke(usr3).toCompletableFuture().get(3, SECONDS);

      friendService.addFriend().invoke("usr1", new FriendId(usr2.userId)).toCompletableFuture().get(3, SECONDS);
      friendService.addFriend().invoke("usr1", new FriendId(usr3.userId)).toCompletableFuture().get(3, SECONDS);

      User fetchedUsr1 = friendService.getUser().invoke("usr1", NotUsed.getInstance()).toCompletableFuture().get(3,
          SECONDS);
      assertEquals(usr1.userId, fetchedUsr1.userId);
      assertEquals(usr1.name, fetchedUsr1.name);
      assertEquals(TreePVector.empty().plus("usr2").plus("usr3"), fetchedUsr1.friends);

      eventually(FiniteDuration.create(10, SECONDS), () -> {
        PSequence<String> followers = friendService.getFollowers().invoke("usr2", NotUsed.getInstance())
            .toCompletableFuture().get(3, SECONDS);
        assertEquals(TreePVector.empty().plus("usr1"), followers);
      });

    });
  }



}
