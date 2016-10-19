/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.cluster.Cluster
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence.testkit.SimulatedNullpointerException
import javax.inject.Inject

object TestEntity {
  // FIXME try with Jsonable
  sealed trait Cmd

  case object Get extends Cmd with ReplyType[State]

  final case class Add(element: String, times: Int = 1) extends Cmd with ReplyType[Evt]

  // TODO how to do serialization of "scala enum", i.e. sealed trait
  sealed trait Mode
  object Mode {
    object Prepend extends Mode
    object Append extends Mode
  }

  final case class ChangeMode(mode: Mode) extends Cmd with ReplyType[Evt]

  case object UndefinedCmd extends Cmd with ReplyType[Evt]

  case object GetAddress extends Cmd with ReplyType[Address]

  object Evt {
    val NumShards = 4
    // second param is optional, defaults to the class name
    val aggregateEventShards = AggregateEventTag.sharded(classOf[Evt], NumShards)
  }

  // FIXME try with Jsonable
  sealed trait Evt extends AggregateEvent[Evt] {
    override def aggregateTag: AggregateEventShards[Evt] = Evt.aggregateEventShards
  }

  final case class Appended(element: String) extends Evt

  final case class Prepended(element: String) extends Evt

  case object InPrependMode extends Evt

  case object InAppendMode extends Evt

  object State {
    val empty: State = State(Mode.Append, Nil)
  }

  // FIXME try with Jsonable
  final case class State(mode: Mode, elements: List[String]) {
    def add(elem: String): State = mode match {
      case Mode.Prepend => new State(mode, elem +: elements)
      case Mode.Append  => new State(mode, elements :+ elem)
    }
  }

  // TestProbe message
  final case class AfterRecovery(state: State)
}

class TestEntity @Inject() (system: ActorSystem, probe: Option[ActorRef] = None)
  extends PersistentEntity[TestEntity.Cmd, TestEntity.Evt, TestEntity.State] {
  import TestEntity._

  override def initialState: State = State.empty

  override def behavior: Behavior = {
    case State(Mode.Append, _)  => appending
    case State(Mode.Prepend, _) => prepending
  }

  private val changeMode: CommandHandler = {
    case (c @ ChangeMode(mode), ctx, state) => {
      mode match {
        case mode if state.mode == mode => ctx.done
        case Mode.Append                => ctx.thenPersist(InAppendMode, ctx.reply(c, _))
        case Mode.Prepend               => ctx.thenPersist(InPrependMode, ctx.reply(c, _))
      }
    }
  }

  val baseActions: Actions = {
    Actions()
      .onReadOnlyCommand {
        case (Get, ctx, state)        => ctx.reply(Get, state)
        case (GetAddress, ctx, state) => ctx.reply(GetAddress, Cluster.get(system).selfAddress)
      }
      .onCommand(changeMode)
  }

  private val appending: Actions =
    baseActions
      .onEvent {
        case (Appended(elem), state) => state.add(elem)
        case (InPrependMode, state)  => state.copy(mode = Mode.Prepend)
      }
      .onCommand {
        case (a @ Add(elem, times), ctx, state) =>
          // note that null should trigger NPE, for testing exception
          if (elem == null)
            throw new SimulatedNullpointerException
          if (elem.length == 0) {
            ctx.invalidCommand("element must not be empty");
            ctx.done
          }
          val appended = Appended(elem.toUpperCase)
          if (times == 1)
            ctx.thenPersist(appended, ctx.reply(a, _))
          else
            ctx.thenPersistAll(List.fill(times)(appended), () => ctx.reply(a, appended))
      }

  private val prepending: Actions =
    baseActions
      .onEvent {
        case (Prepended(elem), state) => state.add(elem)
        case (InAppendMode, state)    => state.copy(mode = Mode.Append)
      }
      .onCommand {
        case (a @ Add(elem, times), ctx, state) =>
          if (elem == null || elem.length == 0) {
            ctx.invalidCommand("element must not be empty");
            ctx.done
          }
          val prepended = Prepended(elem.toUpperCase)
          if (times == 1)
            ctx.thenPersist(prepended, ctx.reply(a, _))
          else
            ctx.thenPersistAll(List.fill(times)(prepended), () => ctx.reply(a, prepended))
      }

  override def recoveryCompleted(state: State): State = {
    probe.foreach(_ ! AfterRecovery(state))
    state
  }

}
