/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.testkit

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.persistence.PersistentActor
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.serialization.Jsonable

import scala.concurrent.duration._

object AbstractEmbeddedPersistentActorSpec {
  // All commands and events extending Jsonable so that the
  // tests will use Jackson serialization instead of Java's.
  case object Get                    extends Jsonable
  final case class Cmd(data: String) extends Jsonable
  final case class Evt(data: String) extends Jsonable
  final case class State(data: Vector[String] = Vector.empty) extends Jsonable {
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

trait AbstractEmbeddedPersistentActorSpec { spec: ActorSystemSpec =>
  import AbstractEmbeddedPersistentActorSpec._

  "A persistent actor" must {
    "store events in the embedded journal" in within(15.seconds) {
      val p = system.actorOf(props("p1"))
      println(implicitly[ActorRef])

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
