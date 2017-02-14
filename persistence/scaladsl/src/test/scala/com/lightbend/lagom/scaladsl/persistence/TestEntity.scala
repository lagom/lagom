/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.cluster.Cluster
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence.testkit.SimulatedNullpointerException
import com.lightbend.lagom.scaladsl.playjson.{ JsonSerializerRegistry, JsonSerializer }

import scala.collection.immutable

object TestEntity {

  object SharedFormats {
    import play.api.libs.json._

    implicit val modeFormat = Format[Mode](
      Reads[Mode] {
        case JsString("append")  => JsSuccess(Mode.Append)
        case JsString("prepend") => JsSuccess(Mode.Prepend)
        case js                  => JsError(s"unknown mode js: $js")
      }, Writes[Mode] {
        case Mode.Append  => JsString("append")
        case Mode.Prepend => JsString("prepend")
      }
    )
  }

  object Cmd {
    import play.api.libs.json._
    import JsonSerializer.emptySingletonFormat
    import SharedFormats._

    val serializers = Vector(
      JsonSerializer(Json.format[Add]),
      JsonSerializer(Json.format[ChangeMode]),
      JsonSerializer(emptySingletonFormat(Get)),
      JsonSerializer(emptySingletonFormat(UndefinedCmd)),
      JsonSerializer(emptySingletonFormat(GetAddress))
    )

  }

  sealed trait Cmd

  case object Get extends Cmd with ReplyType[State]

  final case class Add(element: String, times: Int = 1) extends Cmd with ReplyType[Evt]

  sealed trait Mode
  object Mode {
    case object Prepend extends Mode
    case object Append extends Mode
  }

  final case class ChangeMode(mode: Mode) extends Cmd with ReplyType[Evt]

  case object UndefinedCmd extends Cmd with ReplyType[Evt]

  case object GetAddress extends Cmd with ReplyType[Address]

  object Evt {
    val NumShards = 4
    // second param is optional, defaults to the class name
    val aggregateEventShards = AggregateEventTag.sharded[Evt](NumShards)

    import play.api.libs.json._
    import JsonSerializer.emptySingletonFormat
    import SharedFormats._

    val serializers = Vector(
      // events
      JsonSerializer(Json.format[Appended]),
      JsonSerializer(Json.format[Prepended]),
      JsonSerializer(emptySingletonFormat(InPrependMode)),
      JsonSerializer(emptySingletonFormat(InAppendMode))
    )
  }

  sealed trait Evt extends AggregateEvent[Evt] {
    override def aggregateTag: AggregateEventShards[Evt] = Evt.aggregateEventShards
  }

  final case class Appended(element: String) extends Evt

  final case class Prepended(element: String) extends Evt

  case object InPrependMode extends Evt

  case object InAppendMode extends Evt

  object State {
    val empty: State = State(Mode.Append, Nil)

    import play.api.libs.json._
    import JsonSerializer.emptySingletonFormat
    import SharedFormats._
    val serializers = Vector(
      JsonSerializer(Json.format[State])
    )
  }

  final case class State(mode: Mode, elements: List[String]) {
    def add(elem: String): State = mode match {
      case Mode.Prepend => new State(mode, elem +: elements)
      case Mode.Append  => new State(mode, elements :+ elem)
    }
  }

  // TestProbe message
  final case class AfterRecovery(state: State)
}

object TestEntitySerializerRegistry extends JsonSerializerRegistry {
  import TestEntity._

  override def serializers: immutable.Seq[JsonSerializer[_]] = Cmd.serializers ++ Evt.serializers ++ State.serializers

}

class TestEntity(system: ActorSystem)
  extends PersistentEntity {
  import TestEntity._

  override type Command = Cmd
  override type Event = Evt
  override type State = TestEntity.State

  def this(system: ActorSystem, probe: Option[ActorRef]) = {
    this(system)
    this.probe = probe
  }

  var probe: Option[ActorRef] = None

  override def initialState: State = State.empty

  override def behavior: Behavior = {
    case State(Mode.Append, _)  => appending
    case State(Mode.Prepend, _) => prepending
  }

  private val changeMode: Actions = {
    Actions()
      .onCommand[ChangeMode, Evt] {
        case (ChangeMode(mode), ctx, state) => {
          mode match {
            case mode if state.mode == mode => ctx.done
            case Mode.Append                => ctx.thenPersist(InAppendMode)(ctx.reply)
            case Mode.Prepend               => ctx.thenPersist(InPrependMode)(ctx.reply)
          }
        }
      }
  }

  val baseActions: Actions = {
    Actions()
      .onReadOnlyCommand[Get.type, State] {
        case (Get, ctx, state) => ctx.reply(state)
      }
      .onReadOnlyCommand[GetAddress.type, Address] {
        case (GetAddress, ctx, state) => ctx.reply(Cluster.get(system).selfAddress)
      }
      .orElse(changeMode)
  }

  private val appending: Actions =
    baseActions
      .onEvent {
        case (Appended(elem), state) => state.add(elem)
        case (InPrependMode, state)  => state.copy(mode = Mode.Prepend)
      }
      .onCommand[Add, Evt] {
        case (Add(elem, times), ctx, state) =>
          // note that null should trigger NPE, for testing exception
          if (elem == null)
            throw new SimulatedNullpointerException
          if (elem.length == 0) {
            ctx.invalidCommand("element must not be empty");
            ctx.done
          }
          val appended = Appended(elem.toUpperCase)
          if (times == 1)
            ctx.thenPersist(appended)(ctx.reply)
          else
            ctx.thenPersistAll(List.fill(times)(appended): _*)(() => ctx.reply(appended))
      }

  private val prepending: Actions =
    baseActions
      .onEvent {
        case (Prepended(elem), state) => state.add(elem)
        case (InAppendMode, state)    => state.copy(mode = Mode.Append)
      }
      .onCommand[Add, Evt] {
        case (Add(elem, times), ctx, state) =>
          if (elem == null || elem.length == 0) {
            ctx.invalidCommand("element must not be empty");
            ctx.done
          }
          val prepended = Prepended(elem.toLowerCase)
          if (times == 1)
            ctx.thenPersist(prepended)(ctx.reply)
          else
            ctx.thenPersistAll(List.fill(times)(prepended): _*)(() => ctx.reply(prepended))
      }

  override def recoveryCompleted(state: State): State = {
    probe.foreach(_ ! AfterRecovery(state))
    state
  }

}
