/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import docs.home.persistence.BlogCommand.*;
import docs.home.persistence.BlogEvent.*;

// #unit-test
import static org.junit.Assert.assertEquals;

import play.inject.guice.GuiceInjectorBuilder;
import play.inject.Injector;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity.InvalidCommandException;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver;
import com.lightbend.lagom.javadsl.testkit.PersistentEntityTestDriver.Outcome;
import com.lightbend.lagom.javadsl.persistence.cassandra.testkit.TestUtil;
import com.lightbend.lagom.javadsl.pubsub.PubSubModule;
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry;
import java.util.Collections;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import akka.Done;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;

import scala.concurrent.ExecutionContext;

import static play.inject.Bindings.bind;

public class Post4Test {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("Post4Test", TestUtil.clusterConfig());
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  private final Injector injector =
      new GuiceInjectorBuilder()
          .bindings(
              bind(ActorSystem.class).toInstance(system),
              bind(Materializer.class).toInstance(ActorMaterializer.create(system)),
              bind(ExecutionContext.class).toInstance(system.dispatcher()))
          .bindings(new PubSubModule())
          .build();
  private final PubSubRegistry pubSub = injector.instanceOf(PubSubRegistry.class);

  @Test
  public void testAddPost() {
    PersistentEntityTestDriver<BlogCommand, BlogEvent, BlogState> driver =
        new PersistentEntityTestDriver<>(system, new Post4(pubSub), "post-1");

    PostContent content = new PostContent("Title", "Body");
    Outcome<BlogEvent, BlogState> outcome = driver.run(new AddPost(content));
    assertEquals(new PostAdded("post-1", content), outcome.events().get(0));
    assertEquals(1, outcome.events().size());
    assertEquals(false, outcome.state().isPublished());
    assertEquals(Optional.of(content), outcome.state().getContent());
    assertEquals(new AddPostDone("post-1"), outcome.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome.issues());
  }

  @Test
  public void testInvalidTitle() {
    PersistentEntityTestDriver<BlogCommand, BlogEvent, BlogState> driver =
        new PersistentEntityTestDriver<>(system, new Post4(pubSub), "post-1");

    Outcome<BlogEvent, BlogState> outcome = driver.run(new AddPost(new PostContent("", "Body")));
    assertEquals(InvalidCommandException.class, outcome.getReplies().get(0).getClass());
    assertEquals(0, outcome.events().size());
    assertEquals(Collections.emptyList(), outcome.issues());
  }

  @Test
  public void testChangeBody() {
    PersistentEntityTestDriver<BlogCommand, BlogEvent, BlogState> driver =
        new PersistentEntityTestDriver<>(system, new Post4(pubSub), "post-1");

    driver.run(new AddPost(new PostContent("Title", "Body")));

    Outcome<BlogEvent, BlogState> outcome =
        driver.run(new ChangeBody("New body 1"), new ChangeBody("New body 2"));

    assertEquals(new BodyChanged("post-1", "New body 1"), outcome.events().get(0));
    assertEquals(new BodyChanged("post-1", "New body 2"), outcome.events().get(1));
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

    driver.run(new AddPost(new PostContent("Title", "Body")));

    Outcome<BlogEvent, BlogState> outcome = driver.run(Publish.INSTANCE);

    assertEquals(true, outcome.state().isPublished());
    assertEquals(1, outcome.events().size());
    assertEquals(new PostPublished("post-1"), outcome.events().get(0));
    assertEquals(1, outcome.getReplies().size());
    assertEquals(Done.getInstance(), outcome.getReplies().get(0));
    assertEquals(Collections.emptyList(), outcome.issues());
  }
}
// #unit-test
