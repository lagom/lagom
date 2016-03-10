package docs.home.persistence;

//#unit-test
import static org.junit.Assert.assertEquals;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity.InvalidCommandException;
import com.lightbend.lagom.javadsl.cluster.testkit.ActorSystemModule;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver.Outcome;
import com.lightbend.lagom.javadsl.persistence.testkit.TestUtil;
import com.lightbend.lagom.javadsl.pubsub.PubSubModule;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;
import java.util.Collections;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import akka.Done;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;

public class Post4Test {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("Post4Test", TestUtil.clusterConfig());
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  private final Injector injector = Guice.createInjector(
      new ActorSystemModule(system), new PubSubModule());
  private final PubSubRegistry pubSub = injector.getInstance(PubSubRegistry.class);

  @Test
  public void testAddPost() {
    PersistentEntityTestDriver<BlogCommand, BlogEvent, BlogState> driver =
        new PersistentEntityTestDriver<>(system, new Post4(pubSub), "post-1");

    PostContent content = PostContent.of("Title", "Body");
    Outcome<BlogEvent, BlogState> outcome = driver.run(
        AddPost.of(content));
    assertEquals(PostAdded.builder().content(content).postId("post-1").build(),
        outcome.events().get(0));
    assertEquals(1, outcome.events().size());
    assertEquals(false, outcome.state().isPublished());
    assertEquals(Optional.of(content), outcome.state().getContent());
    assertEquals(AddPostDone.of("post-1"), outcome.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome.issues());
  }

  @Test
  public void testInvalidTitle() {
    PersistentEntityTestDriver<BlogCommand, BlogEvent, BlogState> driver =
        new PersistentEntityTestDriver<>(system, new Post4(pubSub), "post-1");

    Outcome<BlogEvent, BlogState> outcome = driver.run(
        AddPost.of(PostContent.of("", "Body")));
    assertEquals(InvalidCommandException.class,
        outcome.getReplies().get(0).getClass());
    assertEquals(0, outcome.events().size());
    assertEquals(Collections.emptyList(), outcome.issues());
  }

  @Test
  public void testChangeBody() {
    PersistentEntityTestDriver<BlogCommand, BlogEvent, BlogState> driver =
        new PersistentEntityTestDriver<>(system, new Post4(pubSub), "post-1");

    driver.run(AddPost.of(PostContent.of("Title", "Body")));

    Outcome<BlogEvent, BlogState> outcome = driver.run(
      ChangeBody.of("New body 1"),
      ChangeBody.of("New body 2"));

    assertEquals(BodyChanged.of("New body 1"), outcome.events().get(0));
    assertEquals(BodyChanged.of("New body 2"), outcome.events().get(1));
    assertEquals(2, outcome.events().size());
    assertEquals(false, outcome.state().isPublished());
    assertEquals("New body 2", outcome.state().getContent().get().getBody());
    assertEquals(Done.getInstance(), outcome.getReplies().get(0));
    assertEquals(Done.getInstance(), outcome.getReplies().get(1));
    assertEquals(2, outcome.getReplies().size());
    assertEquals(Collections.emptyList(), outcome.issues());
  }

  @Test
  public void testPublish() {
    PersistentEntityTestDriver<BlogCommand, BlogEvent, BlogState> driver =
        new PersistentEntityTestDriver<>(system, new Post4(pubSub), "post-1");

    driver.run(AddPost.of(PostContent.of("Title", "Body")));

    Outcome<BlogEvent, BlogState> outcome = driver.run(Publish.of());

    assertEquals(true, outcome.state().isPublished());
    assertEquals(1, outcome.events().size());
    assertEquals(PostPublished.builder().postId("post-1").build(),
        outcome.events().get(0));
    assertEquals(1, outcome.getReplies().size());
    assertEquals(Done.getInstance(), outcome.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome.issues());
  }

}
//#unit-test
