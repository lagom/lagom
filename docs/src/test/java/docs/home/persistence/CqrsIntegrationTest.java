package docs.home.persistence;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.japi.Pair;
import akka.japi.function.Effect;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.scaladsl.TestSink;
import akka.testkit.javadsl.TestKit;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.client.ConfigurationServiceLocator;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import com.lightbend.lagom.javadsl.persistence.cassandra.testkit.TestUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import docs.home.persistence.BlogCommand.AddPost;
import docs.home.persistence.BlogEvent.PostAdded;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Application;
import play.inject.Injector;
import play.inject.guice.GuiceApplicationBuilder;
import scala.collection.JavaConverters;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;
import static play.inject.Bindings.bind;


public class CqrsIntegrationTest {

  static ActorSystem system;
  static Injector injector;
  static Application application;
  static CassandraSession cassandraSession;
  private FiniteDuration defaultTimeout = new FiniteDuration(18, TimeUnit.SECONDS);

  @BeforeClass
  public static void setup() throws Exception {

    Config config = TestUtil
        .persistenceConfig("CqrsIntegrationTest", CassandraLauncher.randomPort())
        // Following sets the default timeout on Akka TestProbes.
        .withValue(
            "akka.test.single-expect-default",
            ConfigValueFactory.fromAnyRef("19s"));

    File cassandraDirectory = new File("target/CqrsIntegrationTest");
    CassandraLauncher.start(cassandraDirectory, "lagom-test-embedded-cassandra.yaml", true, 0);

    application = new GuiceApplicationBuilder()
        .configure(config)
        .bindings(bind(ServiceLocator.class).to(ConfigurationServiceLocator.class))
        .build();

    injector = application.injector();
    system = injector.instanceOf(ActorSystem.class);
    cassandraSession = injector.instanceOf(CassandraSession.class);

    TestUtil.awaitPersistenceInit(system);

    Cluster.get(system).join(Cluster.get(system).selfAddress());

  }

  @AfterClass
  public static void teardown() {
    application.asScala().stop();
    application = null;
    system = null;
    injector = null;
    cassandraSession = null;
    CassandraLauncher.stop();
  }

  private PersistentEntityRegistry registry() {
    PersistentEntityRegistry reg = injector.instanceOf(PersistentEntityRegistry.class);
    reg.register(Post.class);
    return reg;
  }

  private ReadSide readSide() {
    return injector.instanceOf(ReadSide.class);
  }

  private void eventually(Effect block) {
    new TestKit(system) {
      {
        awaitAssert(Duration.ofSeconds(20), () -> {
          try {
            block.apply();
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          return null;
        });
      }
    };
  }

  // DISCLAIMER: This tests uses non-uniform timeout values (13s, 14s, 15s, ...) as a good practice to help isolate
  // the cause of a timeout in case of test failure.

  @Test
  public void testAddBlogPostsAndUpdateReadSide() throws Exception {

    // At system startup event processor is started.
    // It consumes the stream of persistent events, here BlogEvent subclasses.
    // It will update the blogsummary table.
    readSide().register(CassandraBlogEventProcessor.BlogEventProcessor.class);

    // persist some events via the Post PersistentEntity
    final PersistentEntityRef<BlogCommand> ref1 = registry().refFor(Post.class, "1");
    final AddPost cmd1 = new AddPost(new PostContent("Title 1", "Body"));
    ref1.ask(cmd1).toCompletableFuture().get(13, SECONDS); // await only for deterministic order

    final PersistentEntityRef<BlogCommand> ref2 = registry().refFor(Post.class, "2");
    final AddPost cmd2 = new AddPost(new PostContent("Title 2", "Body"));
    ref2.ask(cmd2).toCompletableFuture().get(14, SECONDS); // await only for deterministic order

    final Materializer mat = ActorMaterializer.create(system);


    // Eventually (when the BlogEventProcessor is ready), we can create a PreparedStatement to query the
    // blogsummary table via the CassandraSession,
    // e.g. a Service API request
    eventually(() -> {
      // the creation of this PreparedStatement will fail while `blogsummary` doesn't exist.
      cassandraSession.prepare("SELECT id, title FROM blogsummary").toCompletableFuture().get(5, SECONDS);
    });


    final PreparedStatement selectStmt = cassandraSession.prepare("SELECT id, title FROM blogsummary")
            .toCompletableFuture().get(15, SECONDS);
    final BoundStatement boundSelectStmt = selectStmt.bind();

    eventually(() -> {
      // stream from a Cassandra select result set, e.g. response to a Service API request
      final Source<String, ?> queryResult = cassandraSession.select(boundSelectStmt).map(row -> row.getString("title"));

      final TestSubscriber.Probe<String> probe = queryResult.runWith(TestSink.probe(system), mat).request(10);
      probe.expectNextUnordered("Title 1", "Title 2");
      probe.expectComplete();
    });



    // persist something more, the processor will consume the event
    // and update the blogsummary table
    final PersistentEntityRef<BlogCommand> ref3 = registry().refFor(Post.class, "3");
    final AddPost cmd3 = new AddPost(new PostContent("Title 3", "Body"));
    ref3.ask(cmd3).toCompletableFuture().get(16, SECONDS);

    eventually(() -> {
      final Source<String, ?> queryResult = cassandraSession.select(boundSelectStmt).map(row -> row.getString("title"));

      final TestSubscriber.Probe<String> probe = queryResult.runWith(TestSink.probe(system), mat).request(10);
      probe.expectNextUnordered("Title 1", "Title 2", "Title 3");
      probe.expectComplete();
    });



    // For other use cases than updating a read-side table in Cassandra it is possible
    // to consume the events directly.
    final Source<Pair<BlogEvent, Offset>, ?> eventStream = Source.from(BlogEvent.TAG.allTags())
            .flatMapMerge(BlogEvent.TAG.numShards(), tag -> registry().eventStream(tag, Offset.NONE));

    final TestSubscriber.Probe<BlogEvent> eventProbe = eventStream.map(pair -> pair.first())
        .runWith(TestSink.probe(system), mat);
    eventProbe.request(4);
    List<BlogEvent> events = JavaConverters.seqAsJavaList(eventProbe.expectNextN(3));

    assertThat(events, hasItem(new PostAdded("1", new PostContent("Title 1", "Body"))));
    assertThat(events, hasItem(new PostAdded("2", new PostContent("Title 2", "Body"))));
    assertThat(events, hasItem(new PostAdded("3", new PostContent("Title 3", "Body"))));

    final PersistentEntityRef<BlogCommand> ref4 = registry().refFor(Post.class, "4");
    final AddPost cmd4 = new AddPost(new PostContent("Title 4", "Body"));
    ref4.ask(cmd4).toCompletableFuture().get(17, SECONDS);

    eventProbe.expectNext(
        defaultTimeout,
        new PostAdded("4", new PostContent("Title 4", "Body")));

  }

}
