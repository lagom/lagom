/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.testkit
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Scheduler
import akka.pattern._
import akka.persistence.PersistentActor
import akka.testkit.TestProbe
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

// A copy of akka.persistence.cassandra.CassandraLifecycle's awaitPersistenceInit.
private[lagom] object AwaitPersistenceInit {

  def awaitPersistenceInit(system: ActorSystem): Unit = {

    val log   = LoggerFactory.getLogger(getClass)
    val t0    = System.nanoTime()
    var n     = 0

    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler         = system.scheduler
    implicit val askTimeout: Timeout        = Timeout(250.millis)

    val uuid = UUID.randomUUID().toString
    def askToActor: Future[Done] = {
      n += 1
      val initActor = system.actorOf(Props[AwaitPersistenceInit], s"persistenceInit-$uuid-$n")
      initActor.ask("hello").map(_ => Done)
    }

    // retry for at least 15 seconds
    // (askTimeout + delay) * attempts = (250 + 50) * 50
    val result = retry(() => askToActor, attempts = 50, delay = 50.millis)

    result.onComplete { _ =>
      log.debug(
        "awaitPersistenceInit took {} ms {}",
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
        system.name
      )
    }

    Await.ready(result, 15.seconds)
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
