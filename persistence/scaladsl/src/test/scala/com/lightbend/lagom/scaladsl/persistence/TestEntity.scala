/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import com.lightbend.lagom.serialization.Jsonable
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import akka.actor.Address
import akka.actor.ActorRef
import akka.actor.ActorSystem
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

  private val defaultCommandHandlers: PartialFunction[Any, Function[CommandContext[Any], Option[Persist[_ <: Evt]]]] = {
    case ChangeMode(mode) => ctx => mode match {
      case mode if state.mode == mode => Some(ctx.done())
      case Mode.Append                => Some(ctx.thenPersist(InAppendMode, ctx.reply))
      case Mode.Prepend               => Some(ctx.thenPersist(InPrependMode, ctx.reply))
    }
    case Get => ctx =>
      ctx.reply(state)
      Some(ctx.done())
    case GetAddress => ctx =>
      //ctx.reply(Cluster.get(system).selfAddress())
      Some(ctx.done())
  }

  private def addCommandHandler(eventFactory: String => Evt): PartialFunction[Any, Function[CommandContext[Any], Option[Persist[_ <: Evt]]]] = {
    case Add(element, times) => ctx =>
      if (element == null) {
        throw new NullPointerException() //SimulatedNullpointerException()
      }
      if (element.length == 0) {
        ctx.invalidCommand("element must not be empty")
        Some(ctx.done())
      } else {
        val a = eventFactory(element.toUpperCase())
        if (times == 1) {
          Some(ctx.thenPersist(a, ctx.reply))
        } else {
          Some(ctx.thenPersistAll(List.fill(times)(a), () => ctx.reply(a)))
        }
      }
  }

  private val defaultEventHandlers: PartialFunction[Any, Behavior] = {
    case Appended(el)  => behavior.transformState(_.add(el))
    case Prepended(el) => behavior.transformState(_.add(el))
    case InAppendMode  => buildBehavior(new Appended(_), state)
    case InPrependMode => buildBehavior(new Prepended(_), state)
  }

  private def buildBehavior(addEvent: String => Evt, state: State): Behavior = {
    val function: PartialFunction[Any, Function[CommandContext[Any], Option[Persist[_ <: Evt]]]] = (defaultCommandHandlers orElse addCommandHandler(addEvent)).asInstanceOf[PartialFunction[Any, Function[CommandContext[Any], Option[Persist[_ <: Evt]]]]]
    newBehaviorBuilder(state)
      .withCommandHandlers(function)
      .withEventHandlers(defaultEventHandlers)
      .build()
  }

  def initialBehavior(snapshotState: Option[State]): Behavior = {
    val state = snapshotState match {
      case Some(snap) =>
        probe.foreach(_ ! Snapshot(snap))
        snap
      case None => State.empty
    }
    val addEvent = state.mode match {
      case Mode.Append  => new Appended(_)
      case Mode.Prepend => new Prepended(_)
    }
    buildBehavior(addEvent, state)
  }

  override def recoveryCompleted(): Behavior = {
    probe.foreach(_ ! AfterRecovery(state))
    behavior
  }

}
