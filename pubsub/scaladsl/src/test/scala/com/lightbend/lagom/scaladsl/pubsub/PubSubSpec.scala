/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.pubsub

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import com.lightbend.lagom.internal.scaladsl.PubSubRegistryImpl
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.Await
import scala.concurrent.duration._

class PubSubSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  val app = new PubSubComponents {
    override lazy val actorSystem = {
      val config = ConfigFactory.parseString("""
      akka.actor.provider = akka.cluster.ClusterActorRefProvider
      akka.remote.netty.tcp.port = 0
      akka.remote.netty.tcp.hostname = 127.0.0.1
      akka.loglevel = INFO
    """)
      ActorSystem("PubSubTest", config)
    }
  }
  val system = app.actorSystem
  Cluster.get(system).join(Cluster.get(system).selfAddress)
  implicit val mat = ActorMaterializer.create(system)
  val registry = app.pubSubRegistry

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  def awaitHasSubscribers(ref: PubSubRef[_], expected: Boolean) = {
    TestKit.awaitCond(Await.result(ref.hasAnySubscribers(), 10.seconds) == expected, 20.seconds)
  }

  "PubSub" should {
    "publish single messages" in {
      val topic = TopicId[Notification]
      val ref = registry.refFor(topic)
      val sub = ref.subscriber
      val probe = sub.map(_.msg).runWith(TestSink.probe(system)).request(2)

      awaitHasSubscribers(ref, true)

      ref.publish(Notification("hello"))
      ref.publish(Notification("hi again"))
      probe.expectNext("hello")
      probe.expectNext("hi again")
    }

    "publish streams of messages" in {
      val topic = TopicId[Notification]("1")
      val ref = registry.refFor(topic)
      val sub = ref.subscriber
      val probe = sub.map(_.msg).runWith(TestSink.probe(system)).request(2)

      awaitHasSubscribers(ref, true)

      val pub = ref.publisher
      Source(List(Notification("hello-1"), Notification("hello-2"), Notification("hello-3"))).runWith(pub)
      probe.expectNext("hello-1")
      probe.expectNext("hello-2")
      probe.expectNoMsg(100.milliseconds)
      probe.request(10)
      probe.expectNext("hello-3")
    }

    "publish to multiple subscribers" in {
      val topic = TopicId[Notification]("2")
      val ref = registry.refFor(topic)
      val sub = ref.subscriber.map(_.msg)
      val probe1 = sub.runWith(TestSink.probe(system)).request(2)
      val probe2 = sub.runWith(TestSink.probe(system)).request(2)

      awaitHasSubscribers(ref, true)

      ref.publish(Notification("hello"))
      probe1.expectNext("hello")
      probe2.expectNext("hello")
    }

    "continue publishing after a subscriber has cancelled" in {
      val topic = TopicId[Notification]("3")
      val ref = registry.refFor(topic)
      val sub = ref.subscriber.map(_.msg)
      val probe1 = sub.runWith(TestSink.probe(system)).request(2)

      awaitHasSubscribers(ref, true)

      ref.publish(Notification("hello"))
      probe1.expectNext("hello")
      probe1.cancel()

      val probe2 = sub.runWith(TestSink.probe(system)).request(2)
      awaitHasSubscribers(ref, true)
      ref.publish(Notification("hello2"))
      probe2.expectNext("hello2")
    }

    "publish multiple streams" in {
      val topic = TopicId[Notification]("4")
      val ref = registry.refFor(topic)
      val sub = ref.subscriber
      val probe = sub.map(_.msg).runWith(TestSink.probe(system)).request(10)
      awaitHasSubscribers(ref, true)

      val pub = ref.publisher
      Source(List(Notification("hello-1a"), Notification("hello-2a"), Notification("hello-3a"))).runWith(pub)
      Source(List(Notification("hello-1b"), Notification("hello-2b"), Notification("hello-3b"))).runWith(pub)
      probe.expectNextUnordered("hello-1a", "hello-1b", "hello-2a", "hello-2b", "hello-3a", "hello-3b")
    }

    "continue publishing after publisher finishes" in {
      val topic = TopicId[Notification]("5")
      val ref = registry.refFor(topic)
      val sub = ref.subscriber.map(_.msg)
      val pub = ref.publisher

      val probe1 = sub.runWith(TestSink.probe(system)).request(10)
      awaitHasSubscribers(ref, true)
      Source(List(Notification("hello-1a"), Notification("hello-2a"), Notification("hello-3a"))).runWith(pub)
      probe1.expectNext("hello-1a")
      probe1.expectNext("hello-2a")
      probe1.expectNext("hello-3a")
      probe1.cancel

      val probe2 = sub.runWith(TestSink.probe(system)).request(10)
      Source(List(Notification("hello-1b"), Notification("hello-2b"), Notification("hello-3b"))).runWith(pub)
      probe2.expectNext("hello-1b")
      probe2.expectNext("hello-2b")
      probe2.expectNext("hello-3b")
    }

    "drop the oldest messages when the buffer overflows" in {
      val topic = TopicId[Notification]("6")
      val config = ConfigFactory.parseString("subscriber-buffer-size = 3").withFallback(system.settings.config.getConfig("lagom.pubsub"))
      val registry = new PubSubRegistryImpl(system, config)
      val ref = registry.refFor(topic)
      val sub = ref.subscriber

      // important to not use any intermediate stages (such as map) here, because then
      // internal buffering comes into play
      val probe = sub.runWith(TestSink.probe(system)).request(2)
      awaitHasSubscribers(ref, true)
      for (i <- 1 to 10) yield ref.publish(Notification("hello-" + i))
      probe.expectNext(Notification("hello-1"))
      probe.expectNext(Notification("hello-2"))
      probe.expectNoMsg(1.second)
      probe.request(100)
      probe.expectNext(Notification("hello-8"))
      probe.expectNext(Notification("hello-9"))
      probe.expectNext(Notification("hello-10"))
      probe.expectNoMsg(100.milliseconds)
    }
  }

}
