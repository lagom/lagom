/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.testkit

import java.util.concurrent.TimeUnit

import akka.actor.{ ActorSystem, Props }
import akka.persistence.PersistentActor
import akka.testkit.TestProbe
import com.lightbend.lagom.internal.persistence.testkit
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object AbstractTestUtil {
  class AwaitPersistenceInit extends PersistentActor {
    def persistenceId: String = self.path.name

    def receiveRecover: Receive = {
      case _ =>
    }

    def receiveCommand: Receive = {
      case msg =>
        persist(msg) { _ =>
          sender() ! msg
          context.stop(self)
        }
    }
  }
}

trait AbstractTestUtil {
  def clusterConfig(): Config = testkit.clusterConfig

  def awaitPersistenceInit(system: ActorSystem): Unit = {
    val probe = TestProbe()(system)
    val log = LoggerFactory.getLogger(getClass)
    val t0 = System.nanoTime()
    var n = 0
    probe.within(45.seconds) {
      probe.awaitAssert {
        n += 1
        system.actorOf(Props[AbstractTestUtil.AwaitPersistenceInit], "persistenceInit" + n).tell("hello", probe.ref)
        probe.expectMsg(5.seconds, "hello")
        log.debug("awaitPersistenceInit took {} ms {}", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0), system.name)
      }
    }
  }

}
