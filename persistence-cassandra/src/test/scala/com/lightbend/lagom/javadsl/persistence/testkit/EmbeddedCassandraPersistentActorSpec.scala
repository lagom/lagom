/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.testkit

import scala.concurrent.duration._
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.actor.actorRef2Scala
import com.lightbend.lagom.javadsl.persistence.PersistenceSpec
import scala.Vector

object EmbeddedCassandraPersistentActorSpec {

  final case class Cmd(data: String)
  final case class Evt(data: String)
  case object Get
  final case class State(data: Vector[String] = Vector.empty) {
    def apply(evt: Evt): State = {
      copy(data :+ evt.data)
    }
  }

  def props(persistenceId: String): Props =
    Props(new Persistent(persistenceId))

  class Persistent(override val persistenceId: String) extends PersistentActor {
    var state = State()

    override def receiveRecover = {
      case evt: Evt => state = state(evt)
    }

    override def receiveCommand = {
      case Cmd(data) =>
        persist(Evt(data.toUpperCase)) { evt =>
          state = state(evt)
        }
      case Get => sender() ! state
    }
  }

}

class EmbeddedCassandraPersistentActorSpec extends PersistenceSpec {
  import EmbeddedCassandraPersistentActorSpec._

  "A persistent actor" must {
    "store events in the embedded Cassandra journal" in within(15.seconds) {
      val p = system.actorOf(props("p1"))
      p ! Get
      expectMsg(State())
      p ! Cmd("a")
      p ! Cmd("b")
      p ! Cmd("c")
      p ! Get
      expectMsg(State(Vector("A", "B", "C")))

      // start another with same persistenceId should recover state
      val p2 = system.actorOf(props("p1"))
      p2 ! Get
      expectMsg(State(Vector("A", "B", "C")))
    }

  }

}

