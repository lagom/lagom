/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.pubsub;

import com.lightbend.lagom.internal.javadsl.pubsub.PubSubRegistryImpl;

import com.lightbend.lagom.javadsl.pubsub.PubSubModule;
import com.lightbend.lagom.javadsl.cluster.testkit.ActorSystemModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.JavaTestKit;

public class PubSubTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    Config config = ConfigFactory.parseString(
        "akka.actor.provider = akka.cluster.ClusterActorRefProvider \n" +
        "akka.remote.netty.tcp.port = 0 \n" +
        "akka.remote.netty.tcp.hostname = 127.0.0.1 \n" +
        "akka.loglevel = INFO \n");

    system = ActorSystem.create("PubSubTest", config);

    Cluster.get(system).join(Cluster.get(system).selfAddress());
  }

  // yeah, the Akka testkit is in need of some Java 8 love
  private void awaitHasSubscribers(PubSubRef<?> ref, boolean expected) {
    new JavaTestKit(system) {
      {
        new AwaitCond(Duration.create(10, TimeUnit.SECONDS)) {
          @Override
          protected boolean cond() {
            try {
              return expected == ref.hasAnySubscribers().toCompletableFuture().get();
            } catch (Exception e) {
              return false;
            }
          }
        };
      }
    };
  }


  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  private final Injector injector = Guice.createInjector(new ActorSystemModule(system), new PubSubModule());

  private PubSubRegistry registry() {
    return injector.getInstance(PubSubRegistry.class);
  }

  @Test
  public void testSimplePubSub() throws Exception {
    final Materializer mat = ActorMaterializer.create(system);
    final TopicId<Notification> topic = new TopicId<>(Notification.class, "");
    final PubSubRef<Notification> ref = registry().refFor(topic);

    final Source<Notification, ?> sub = ref.subscriber();
    final TestSubscriber.Probe<String> probe = sub
      .map(notification -> notification.getMsg())
      .runWith(TestSink.probe(system), mat)
      .request(2);


    awaitHasSubscribers(ref, true);

    ref.publish(new Notification("hello"));
    ref.publish(new Notification("hi again"));

    probe.expectNext("hello");
    probe.expectNext("hi again");
  }

  @Test
  public void testStreamingPublish() throws Exception {
    final Materializer mat = ActorMaterializer.create(system);
    final TopicId<Notification> topic = new TopicId<>(Notification.class, "1");
    final PubSubRef<Notification> ref = registry().refFor(topic);

    final Source<Notification, ?> sub = ref.subscriber();
    final TestSubscriber.Probe<String> probe = sub.map(notification -> notification.getMsg())
        .runWith(TestSink.probe(system), mat).request(2);

    awaitHasSubscribers(ref, true);

    final Sink<Notification, ?> pub = ref.publisher();
    Source.from(
        Arrays.asList(new Notification("hello-1"), new Notification("hello-2"), new Notification("hello-3")))
        .runWith(pub, mat);

    probe.expectNext("hello-1");
    probe.expectNext("hello-2");
    probe.expectNoMsg(Duration.create(100, TimeUnit.MILLISECONDS));

    probe.request(10);
    probe.expectNext("hello-3");
  }

  @Test
  public void testSubscribeMaterializeTwo() throws Exception {
    final Materializer mat = ActorMaterializer.create(system);
    final TopicId<Notification> topic = new TopicId<>(Notification.class, "2");
    final PubSubRef<Notification> ref = registry().refFor(topic);

    final Source<String, ?> src = ref.subscriber()
        .map(notification -> notification.getMsg());
    final TestSubscriber.Probe<String> probe1 = src
      .runWith(TestSink.probe(system), mat)
      .request(2);
    final TestSubscriber.Probe<String> probe2 = src
        .runWith(TestSink.probe(system), mat)
        .request(2);

    awaitHasSubscribers(ref, true);

    ref.publish(new Notification("hello"));

    probe1.expectNext("hello");
    probe2.expectNext("hello");
  }

  @Test
  public void testSubscribeMaterializeMoreThanOnce() throws Exception {
    final Materializer mat = ActorMaterializer.create(system);
    final TopicId<Notification> topic = new TopicId<>(Notification.class, "3");
    final PubSubRef<Notification> ref = registry().refFor(topic);

    final Source<String, ?> src = ref.subscriber()
        .map(notification -> notification.getMsg());
    final TestSubscriber.Probe<String> probe1 = src.runWith(TestSink.probe(system), mat).request(2);

    awaitHasSubscribers(ref, true);

    ref.publish(new Notification("hello"));

    probe1.expectNext("hello");
    probe1.cancel();

    final TestSubscriber.Probe<String> probe2 = src.runWith(TestSink.probe(system), mat).request(2);
    ref.publish(new Notification("hello2"));
    probe2.expectNext("hello2");
  }

  @Test
  public void testPublishMaterializeTwo() throws Exception {
    final Materializer mat = ActorMaterializer.create(system);
    final TopicId<Notification> topic = new TopicId<>(Notification.class, "4");
    final PubSubRef<Notification> ref = registry().refFor(topic);

    final Source<Notification, ?> sub = ref.subscriber();
    TestSubscriber.Probe<String> subProbe = sub.map(notification -> notification.getMsg())
        .runWith(TestSink.probe(system), mat).request(10);

    awaitHasSubscribers(ref, true);

    final Sink<Notification, ?> pub = ref.publisher();
    Source
        .from(Arrays.asList(new Notification("hello-1a"), new Notification("hello-2a"), new Notification("hello-3a")))
        .runWith(pub, mat);
    Source
        .from(Arrays.asList(new Notification("hello-1b"), new Notification("hello-2b"), new Notification("hello-3b")))
        .runWith(pub, mat);

    subProbe.expectNextUnordered("hello-1a", "hello-1b", "hello-2a", "hello-2b", "hello-3a", "hello-3b");
  }

  @Test
  public void testPublishMaterializeMoreThanOnce() throws Exception {
    final Materializer mat = ActorMaterializer.create(system);
    final TopicId<Notification> topic = new TopicId<>(Notification.class, "5");
    final PubSubRef<Notification> ref = registry().refFor(topic);

    final Source<Notification, ?> sub = ref.subscriber();
    final TestSubscriber.Probe<String> probe1 = sub.map(notification -> notification.getMsg())
        .runWith(TestSink.probe(system), mat).request(10);

    awaitHasSubscribers(ref, true);

    final Sink<Notification, ?> pub = ref.publisher();
    Source
        .from(Arrays.asList(new Notification("hello-1a"), new Notification("hello-2a"), new Notification("hello-3a")))
        .runWith(pub, mat);

    probe1.expectNext("hello-1a");
    probe1.expectNext("hello-2a");
    probe1.expectNext("hello-3a");
    probe1.cancel();

    final TestSubscriber.Probe<String> probe2 = sub.map(notification -> notification.getMsg())
        .runWith(TestSink.probe(system), mat).request(10);
    Source
        .from(Arrays.asList(new Notification("hello-1b"), new Notification("hello-2b"), new Notification("hello-3b")))
        .runWith(pub, mat);
    probe2.expectNext("hello-1b");
    probe2.expectNext("hello-2b");
    probe2.expectNext("hello-3b");
  }

  @Test
  public void testDropOldestWhenBufferOverflow() throws Exception {
    final Materializer mat = ActorMaterializer.create(system);
    final TopicId<Notification> topic = new TopicId<>(Notification.class, "7");
    final Config conf = ConfigFactory.parseString("subscriber-buffer-size = 3").withFallback(
        system.settings().config().getConfig("lagom.pubsub"));
    final PubSubRegistry registry = new PubSubRegistryImpl(system, conf);
    final PubSubRef<Notification> ref = registry.refFor(topic);

    final Source<Notification, ?> src = ref.subscriber();
    // important to not use any intermediate stages (such as map) here, because then
    // internal buffering comes into play
    final TestSubscriber.Probe<Notification> probe = src.runWith(TestSink.probe(system), mat).request(2);

    awaitHasSubscribers(ref, true);

    for (int i = 1; i <= 10; i++) {
      ref.publish(new Notification("hello-" + i));
    }

    probe.expectNext(new Notification("hello-1"));
    probe.expectNext(new Notification("hello-2"));
    probe.expectNoMsg(Duration.create(1, TimeUnit.SECONDS));
    probe.request(100);
    probe.expectNext(new Notification("hello-8"));
    probe.expectNext(new Notification("hello-9"));
    probe.expectNext(new Notification("hello-10"));
    probe.expectNoMsg(Duration.create(100, TimeUnit.MILLISECONDS));
  }

}
