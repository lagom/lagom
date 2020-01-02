/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.testkit

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.persistence.PersistentActor
import akka.testkit.ImplicitSender
import akka.testkit.TestKitBase
import com.lightbend.lagom.persistence.ActorSystemSpec
import com.lightbend.lagom.persistence.PersistenceSpec
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import scala.collection.immutable
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import scala.concurrent.duration._

object AbstractEmbeddedPersistentActorSpec {
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

  object EmbeddedPersistentActorSerializers extends JsonSerializerRegistry {
    override def serializers: immutable.Seq[JsonSerializer[_]] = {
      import play.api.libs.json._
      import JsonSerializer.emptySingletonFormat

      Vector(
        JsonSerializer(Json.format[Cmd]),
        JsonSerializer(Json.format[Evt]),
        JsonSerializer(emptySingletonFormat(Get)),
        JsonSerializer(Json.format[State])
      )
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
