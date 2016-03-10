package sample.chirper.friend.impl;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Optional;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pcollections.TreePVector;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver.Outcome;

import akka.Done;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import sample.chirper.friend.api.User;
import sample.chirper.friend.impl.FriendCommand.AddFriend;
import sample.chirper.friend.impl.FriendCommand.CreateUser;
import sample.chirper.friend.impl.FriendCommand.GetUser;
import sample.chirper.friend.impl.FriendCommand.GetUserReply;
import sample.chirper.friend.impl.FriendEvent.FriendAdded;
import sample.chirper.friend.impl.FriendEvent.UserCreated;


public class FriendEntityTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("FriendEntityTest");
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testCreateUser() {
    PersistentEntityTestDriver<FriendCommand, FriendEvent, FriendState> driver = new PersistentEntityTestDriver<>(
        system, new FriendEntity(), "user-1");

    Outcome<FriendEvent, FriendState> outcome = driver.run(
        new CreateUser(new User("alice", "Alice")));
    assertEquals(Done.getInstance(), outcome.getReplies().get(0));
    assertEquals("alice", ((UserCreated) outcome.events().get(0)).userId);
    assertEquals("Alice", ((UserCreated) outcome.events().get(0)).name);
    assertEquals(Collections.emptyList(), driver.getAllIssues());
  }

  @Test
  public void testRejectDuplicateCreate() {
    PersistentEntityTestDriver<FriendCommand, FriendEvent, FriendState> driver = new PersistentEntityTestDriver<>(
        system, new FriendEntity(), "user-1");
    driver.run(new CreateUser(new User("alice", "Alice")));

    Outcome<FriendEvent, FriendState> outcome = driver.run(
        new CreateUser(new User("alice", "Alice")));
    assertEquals(PersistentEntity.InvalidCommandException.class, outcome.getReplies().get(0).getClass());
    assertEquals(Collections.emptyList(), outcome.events());
    assertEquals(Collections.emptyList(), driver.getAllIssues());
  }

  @Test
  public void testCreateUserWithInitialFriends() {
    PersistentEntityTestDriver<FriendCommand, FriendEvent, FriendState> driver = new PersistentEntityTestDriver<>(
        system, new FriendEntity(), "user-1");

    TreePVector<String> friends = TreePVector.<String>empty().plus("bob").plus("peter");
    Outcome<FriendEvent, FriendState> outcome = driver.run(
        new CreateUser(new User("alice", "Alice", Optional.of(friends))));
    assertEquals(Done.getInstance(), outcome.getReplies().get(0));
    assertEquals("alice", ((UserCreated) outcome.events().get(0)).userId);
    assertEquals("bob", ((FriendAdded) outcome.events().get(1)).friendId);
    assertEquals("peter", ((FriendAdded) outcome.events().get(2)).friendId);
    assertEquals(Collections.emptyList(), driver.getAllIssues());
  }

  @Test
  public void testAddFriend() {
    PersistentEntityTestDriver<FriendCommand, FriendEvent, FriendState> driver = new PersistentEntityTestDriver<>(
        system, new FriendEntity(), "user-1");
    driver.run(new CreateUser(new User("alice", "Alice")));

    Outcome<FriendEvent, FriendState> outcome = driver.run(new AddFriend("bob"), new AddFriend("peter"));
    assertEquals(Done.getInstance(), outcome.getReplies().get(0));
    assertEquals("bob", ((FriendAdded) outcome.events().get(0)).friendId);
    assertEquals("peter", ((FriendAdded) outcome.events().get(1)).friendId);
    assertEquals(Collections.emptyList(), driver.getAllIssues());
  }

  @Test
  public void testAddDuplicateFriend() {
    PersistentEntityTestDriver<FriendCommand, FriendEvent, FriendState> driver = new PersistentEntityTestDriver<>(
        system, new FriendEntity(), "user-1");
    driver.run(new CreateUser(new User("alice", "Alice")));
    driver.run(new AddFriend("bob"), new AddFriend("peter"));

    Outcome<FriendEvent, FriendState> outcome = driver.run(new AddFriend("bob"));
    assertEquals(Done.getInstance(), outcome.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome.events());
    assertEquals(Collections.emptyList(), driver.getAllIssues());
  }

  @Test
  public void testGetUser() {
    PersistentEntityTestDriver<FriendCommand, FriendEvent, FriendState> driver = new PersistentEntityTestDriver<>(
        system, new FriendEntity(), "user-1");
    User alice = new User("alice", "Alice");
    driver.run(new CreateUser(alice));

    Outcome<FriendEvent, FriendState> outcome = driver.run(new GetUser());
    assertEquals(new GetUserReply(Optional.of(alice)), outcome.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome.events());
    assertEquals(Collections.emptyList(), driver.getAllIssues());
  }

}
