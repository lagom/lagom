/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import com.lightbend.lagom.javadsl.persistence.cassandra.testkit.TestUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity.InvalidCommandException;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity.UnhandledCommandException;
import com.lightbend.lagom.javadsl.persistence.TestEntity.Cmd;
import com.lightbend.lagom.javadsl.persistence.TestEntity.Evt;
import com.lightbend.lagom.javadsl.persistence.TestEntity.State;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Application;
import play.Configuration;
import play.inject.Injector;
import play.inject.guice.GuiceApplicationBuilder;
import scala.concurrent.duration.FiniteDuration;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.pattern.AskTimeoutException;
import akka.persistence.cassandra.testkit.CassandraLauncher;

public class PersistentEntityRefTest {

  static Injector injector;
  static Application application;

  @BeforeClass
  public static void setup() {
    Config config = ConfigFactory.parseString(
        "akka.actor.provider = akka.cluster.ClusterActorRefProvider \n" +
        "akka.remote.netty.tcp.port = 0 \n" +
        "akka.remote.netty.tcp.hostname = 127.0.0.1 \n" +
        "akka.loglevel = INFO \n")
        .withFallback(TestUtil.persistenceConfig("PersistentEntityRefTest", CassandraLauncher.randomPort()));

    application = new GuiceApplicationBuilder()
            .configure(new Configuration(config))
            .build();
    injector = application.injector();

    ActorSystem system = injector.instanceOf(ActorSystem.class);

    Cluster.get(system).join(Cluster.get(system).selfAddress());

    File cassandraDirectory = new File("target/PersistentEntityRefTest");
    CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource(), true, 0);
    TestUtil.awaitPersistenceInit(system);

  }


  @AfterClass
  public static void teardown() {
    application.getWrappedApplication().stop();
    CassandraLauncher.stop();
  }

  public static class AnotherEntity extends PersistentEntity<Integer, String, String> {
    @Override
    public Behavior initialBehavior(Optional<String> snapshotState) {
      return newBehavior("");
    }
  }

  private PersistentEntityRegistry registry() {
    PersistentEntityRegistry reg = injector.instanceOf(PersistentEntityRegistry.class);
    reg.register(TestEntity.class);
    return reg;
  }

  @Test
  public void testSendCommandsToTargetEntity() throws Exception {
    PersistentEntityRef<Cmd> ref1 = registry().refFor(TestEntity.class, "1");
    Evt reply1 = ref1.ask(TestEntity.Add.of("a")).toCompletableFuture().get(15, SECONDS);
    assertEquals(new TestEntity.Appended("1", "A"), reply1);

    PersistentEntityRef<Cmd> ref2 = registry().refFor(TestEntity.class, "2");
    Evt reply2 = ref2.ask(TestEntity.Add.of("b")).toCompletableFuture().get(5, SECONDS);
    assertEquals(new TestEntity.Appended("2", "B"), reply2);

    Evt reply3 = ref2.ask(TestEntity.Add.of("c")).toCompletableFuture().get(5, SECONDS);
    assertEquals(new TestEntity.Appended("2", "C"), reply3);

    State state1 = ref1.ask(TestEntity.Get.instance()).toCompletableFuture().get(5, SECONDS);
    assertEquals(Arrays.asList("A"), state1.getElements());

    State state2 = ref2.ask(TestEntity.Get.instance()).toCompletableFuture().get(5, SECONDS);
    assertEquals(Arrays.asList("B", "C"), state2.getElements());
  }

  @Test(expected = AskTimeoutException.class)
  public void testAskTimeout() throws Throwable {
    PersistentEntityRef<Cmd> ref = registry().refFor(TestEntity.class, "10").withAskTimeout(
        FiniteDuration.create(1, MILLISECONDS));

    List<CompletionStage<Evt>> replies = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      replies.add(ref.ask(TestEntity.Add.of("c")));
    }

    for (CompletionStage<Evt> reply : replies) {
      try {
        reply.toCompletableFuture().get(20, SECONDS);
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    }
  }

  @Test(expected = InvalidCommandException.class)
  public void testInvalidCommand() throws Throwable {
    PersistentEntityRef<Cmd> ref = registry().refFor(TestEntity.class, "10");

    try {
      // empty not allowed
      ref.ask(TestEntity.Add.of("")).toCompletableFuture().get(5, SECONDS);
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }

  @Test(expected = NullPointerException.class)
  public void testThrowException() throws Throwable {
    PersistentEntityRef<Cmd> ref = registry().refFor(TestEntity.class, "10");

    try {
      // null will trigger NPE
      ref.ask(TestEntity.Add.of(null)).toCompletableFuture().get(5, SECONDS);
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }

  @Test(expected = UnhandledCommandException.class)
  public void testUnhandledCommand() throws Throwable {
    PersistentEntityRef<Cmd> ref = registry().refFor(TestEntity.class, "10");

    try {
      // empty not allowed
      ref.ask(new TestEntity.UndefinedCmd()).toCompletableFuture().get(5, SECONDS);
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnregistered() throws Throwable {
    registry().refFor(AnotherEntity.class, "1");
  }

}
