/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.pubsub

import scala.concurrent.duration._
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import akka.remote.testconductor.RoleName
import akka.cluster.Cluster
import java.util.concurrent.CompletionStage
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.lightbend.lagom.javadsl.cluster.testkit.ActorSystemModule
import com.google.inject.Guice

object ClusteredPubSubConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")

  commonConfig(ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    """))

}

class ClusteredPubSubSpecMultiJvmNode1 extends ClusteredPubSubSpec
class ClusteredPubSubSpecMultiJvmNode2 extends ClusteredPubSubSpec

class ClusteredPubSubSpec extends MultiNodeSpec(ClusteredPubSubConfig)
  with STMultiNodeSpec with ImplicitSender {

  import ClusteredPubSubConfig._

  override def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
    }
    enterBarrier(from.name + "-joined")
  }

  override protected def atStartup() {
    roles.foreach(n => join(n, node1))

    enterBarrier("startup")
  }

  implicit val mat = ActorMaterializer()
  val topic1 = TopicId(classOf[Notification], "1")
  val topic2 = TopicId(classOf[Notification], "2")

  val injector = Guice.createInjector(new ActorSystemModule(system), new PubSubModule)

  val registry = injector.getInstance(classOf[PubSubRegistry])

  "PubSub in a Cluster" must {

    "publish messages to subscriber on other node" in within(20.seconds) {
      val ref1 = registry.refFor(topic1)

      runOn(node2) {
        val sub = ref1.subscriber().asScala
        val probe = sub.runWith(TestSink.probe[Notification]).request(10)
        enterBarrier("subscription-established-1")

        probe.expectNext(new Notification("msg-1"))
          .expectNext(new Notification("msg-2"))
      }

      runOn(node1) {
        awaitCond(ref1.hasAnySubscribers().toCompletableFuture.get)
        enterBarrier("subscription-established-1")

        ref1.publish(new Notification("msg-1"))
        ref1.publish(new Notification("msg-2"))
      }

      enterBarrier("after-1")
    }

    "publish stream of messages to subscriber on other node" in within(20.seconds) {
      val ref2 = registry.refFor(topic2)

      runOn(node2) {
        val sub = ref2.subscriber().asScala
        val probe = sub.map(_.getMsg.toUpperCase).runWith(TestSink.probe[String])
          .request(2)
        enterBarrier("subscription-established-2")

        probe.expectNext("A")
          .expectNext("B")
          .expectNoMsg(200.millis)
          .request(2)
          .expectNext("C")
          .expectNext("D")
          .cancel()
          .expectNoMsg(200.millis)

      }

      runOn(node1) {
        awaitCond(ref2.hasAnySubscribers().toCompletableFuture.get)
        enterBarrier("subscription-established-2")

        ref2.publisher().asScala.runWith(
          Source(List("a", "b", "c", "d", "e").map(new Notification(_))))
      }

      enterBarrier("after-2")
    }

  }
}

