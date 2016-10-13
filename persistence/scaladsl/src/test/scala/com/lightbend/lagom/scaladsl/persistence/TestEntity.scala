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
  final case class Snapshot(state: State)
  final case class AfterRecovery(state: State)
}

class TestEntity @Inject() (system: ActorSystem, probe: Option[ActorRef] = None)
  extends PersistentEntity[TestEntity.Cmd, TestEntity.Evt, TestEntity.State] {
  import TestEntity._

  private val changeMode: CommandHandler = {
    case (c @ ChangeMode(mode), ctx, state) => {
      mode match {
        case mode if state.mode == mode => ctx.done
        case Mode.Append                => ctx.thenPersist(InAppendMode, ctx.reply(c, _))
        case Mode.Prepend               => ctx.thenPersist(InPrependMode, ctx.reply(c, _))
      }
    }
  }

  val baseBehavior: Behavior = {
    Behavior(State.empty)
      .addEventHandler {
        case (Appended(elem), b)  => b.mapState(_.add(elem))
        case (Prepended(elem), b) => b.mapState(_.add(elem))
        case (InAppendMode, b)    => becomeAppending(b.state)
        case (InPrependMode, b)   => becomePrepending(b.state)
      }
      .addReadOnlyCommandHandler {
        case (Get, ctx, state)        => ctx.reply(Get, state)
        case (GetAddress, ctx, state) => ctx.reply(GetAddress, Cluster.get(system).selfAddress)
      }
      .addCommandHandler(changeMode)
  }

  override def initialBehavior(snapshotState: Option[State]): Behavior = {
    val state = snapshotState match {
      case Some(snap) =>
        probe.foreach(_ ! Snapshot(snap))
        snap
      case None => State.empty
    }

    state.mode match {
      case Mode.Append  => becomeAppending(state)
      case Mode.Prepend => becomeAppending(state)
    }
  }

  private def becomeAppending(s: State): Behavior = {
    baseBehavior
      .withState(s.copy(mode = Mode.Append))
      .addCommandHandler {
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
  }

  private def becomePrepending(s: State): Behavior = {
    baseBehavior
      .withState(s.copy(mode = Mode.Prepend))
      .addCommandHandler {
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
  }

  override def recoveryCompleted(currentBehavior: Behavior): Behavior = {
    probe.foreach(_ ! AfterRecovery(currentBehavior.state))
    currentBehavior
  }

}
