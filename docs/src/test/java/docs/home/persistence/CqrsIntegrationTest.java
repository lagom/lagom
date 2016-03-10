package docs.home.persistence;

import static java.util.concurrent.TimeUnit.SECONDS;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.lightbend.lagom.javadsl.persistence.PersistenceModule;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import com.lightbend.lagom.javadsl.cluster.testkit.ActorSystemModule;
import com.lightbend.lagom.javadsl.persistence.testkit.TestUtil;
import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

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
import akka.testkit.JavaTestKit;


public class CqrsIntegrationTest {

  static ActorSystem system;
  static Injector injector;
  static CassandraSession cassandraSession;

  @BeforeClass
  public static void setup() throws Exception {
    Config config = TestUtil.persistenceConfig("CqrsIntegrationTest", CassandraLauncher.randomPort(), false);
    system = ActorSystem.create("CqrsIntegrationTest", config);
    injector = Guice.createInjector(new ActorSystemModule(system), new PersistenceModule());
    cassandraSession = injector.getInstance(CassandraSession.class);

    Cluster.get(system).join(Cluster.get(system).selfAddress());

    File cassandraDirectory = new File("target/" + system.name());
    CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource(), true, 0);
    TestUtil.awaitPersistenceInit(system);

    createTable();
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
    injector = null;
    cassandraSession = null;
    CassandraLauncher.stop();
  }

  private static void createTable() throws Exception {
    cassandraSession
        .executeCreateTable(
            "CREATE TABLE IF NOT EXISTS blogsummary (" + "partition int, id text, title text, "
                + "PRIMARY KEY (partition, id))").toCompletableFuture().get(15, SECONDS);

    cassandraSession
        .executeCreateTable(
            "CREATE TABLE IF NOT EXISTS blogevent_offset (" + "partition int, offset timeuuid, "
                + "PRIMARY KEY (partition))").toCompletableFuture().get(15, SECONDS);
  }

  private PersistentEntityRegistry registry() {
    PersistentEntityRegistry reg = injector.getInstance(PersistentEntityRegistry.class);
    reg.register(Post.class);
    return reg;
  }

  private CassandraReadSide readSide() {
    return injector.getInstance(CassandraReadSide.class);
  }

  //yeah, the Akka testkit is in need of some Java 8 love
  private void eventually(Effect block) {
    new JavaTestKit(system) {
      {
        new AwaitAssert(Duration.create(20, TimeUnit.SECONDS)) {
          @Override
          protected void check() {
            try {
              block.apply();
            } catch (RuntimeException e) {
              throw e;
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        };
      }
    };
  }

  @Test
  public void testAddBlogPostsAndUpdateReadSide() throws Exception {

    // At system starup event processor is started.
    // It consumes the stream of persistent events, here BlogEvent subclasses.
    // It will update the blogsummary table.
    readSide().register(BlogEventProcessor.class);

    // persist some events via the Post PersistentEntity
    final PersistentEntityRef<BlogCommand> ref1 = registry().refFor(Post.class, "1");
    final AddPost cmd1 = AddPost.of(PostContent.of("Title 1", "Body"));
    ref1.ask(cmd1).toCompletableFuture().get(15, SECONDS); // await only for deterministic order

    final PersistentEntityRef<BlogCommand> ref2 = registry().refFor(Post.class, "2");
    final AddPost cmd2 = AddPost.of(PostContent.of("Title 2", "Body"));
    ref2.ask(cmd2).toCompletableFuture().get(5, SECONDS); // await only for deterministic order

    // We can query the blogsummary table via the CassandraSession,
    // e.g. a Service API request
    final Materializer mat = ActorMaterializer.create(system);
    final PreparedStatement selectStmt = cassandraSession.prepare("SELECT id, title FROM blogsummary")
        .toCompletableFuture().get(5, SECONDS);
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
    final AddPost cmd3 = AddPost.of(PostContent.of("Title 3", "Body"));
    ref3.ask(cmd3);

    eventually(() -> {
      final Source<String, ?> queryResult = cassandraSession.select(boundSelectStmt).map(row -> row.getString("title"));

      final TestSubscriber.Probe<String> probe = queryResult.runWith(TestSink.probe(system), mat).request(10);
      probe.expectNextUnordered("Title 1", "Title 2", "Title 3");
      probe.expectComplete();
    });

    // For other use cases than updating a read-side table in Cassandra it is possible
    // to consume the events directly.
    final Source<Pair<BlogEvent, UUID>, ?> eventStream = registry()
        .eventStream(BlogEventTag.INSTANCE, Optional.empty());
    final TestSubscriber.Probe<BlogEvent> eventProbe = eventStream.map(pair -> pair.first())
        .runWith(TestSink.probe(system), mat).request(10);
    eventProbe.expectNext(PostAdded.builder().postId("1").content(PostContent.of("Title 1", "Body")).build());
    eventProbe.expectNext(PostAdded.builder().postId("2").content(PostContent.of("Title 2", "Body")).build());
    eventProbe.expectNext(PostAdded.builder().postId("3").content(PostContent.of("Title 3", "Body")).build());

    final PersistentEntityRef<BlogCommand> ref4 = registry().refFor(Post.class, "4");
    final AddPost cmd4 = AddPost.of(PostContent.of("Title 4", "Body"));
    ref4.ask(cmd4);

    eventProbe.expectNext(PostAdded.builder().postId("4").content(PostContent.of("Title 4", "Body")).build());

  }

}
