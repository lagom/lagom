/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.testkit

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.testkit.TestProbe
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

// A copy of akka.persistence.cassandra.CassandraLifecycle's awaitPersistenceInit.
private[lagom] object AwaitPersistenceInit {
  def awaitPersistenceInit(system: ActorSystem): Unit = {
    val probe = TestProbe()(system)
    val log   = LoggerFactory.getLogger(getClass)
    val t0    = System.nanoTime()
    var n     = 0
    probe.within(45.seconds) {
      probe.awaitAssert {
        n += 1
        system.actorOf(Props[AwaitPersistenceInit], "persistenceInit" + n).tell("hello", probe.ref)
        probe.expectMsg(15.seconds, "hello")
        log.debug(
          "awaitPersistenceInit took {} ms {}",
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
          system.name
        )
      }
    }
  }
}

private[lagom] class AwaitPersistenceInit extends PersistentActor {
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
