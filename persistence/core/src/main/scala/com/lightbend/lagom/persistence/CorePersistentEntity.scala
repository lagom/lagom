/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.persistence

import akka.actor.ActorRef

import scala.collection.immutable
import scala.util.control.NoStackTrace
import java.util.function.{ Function => JFunction }
import java.util.function.{ BiFunction => JBiFunction }

object CorePersistentEntity {

  /**
   * Commands to a `PersistentEntity` must implement this interface
   * to define the reply type.
   *
   * `akka.Done` can optionally be used as a "standard" acknowledgment message.
   *
   * Type parameter `R` is the type of the reply message.
   */
  trait ReplyType[R]

  /**
   * Standard exception when rejecting invalid commands.
   */
  final case class InvalidCommandException(message: String) extends IllegalArgumentException(message) with NoStackTrace

  /**
   * Exception that is used when command is not handled
   */
  final case class UnhandledCommandException(message: String) extends IllegalArgumentException(message) with NoStackTrace

  /**
   * Exception that is used when persist fails.
   */
  final case class PersistException(message: String) extends IllegalArgumentException(message) with NoStackTrace

}

/**
 * Common persistent entity methods that are used by the persistence core actors to drive the entity.
 */
trait CorePersistentEntity[Command, Event, State] {

  private var _entityId: String = _

  /**
   * INTERNAL API
   */
  private[lagom] def internalSetEntityId(id: String) = _entityId = id

  private var _behavior: Behavior = _

  /**
   * INTERNAL API
   */
  private[lagom] def internalSetCurrentBehavior(b: Behavior) = _behavior = b

  final def behavior: Behavior = _behavior

  def entityTypeName: String

  /**
   * Internal API
   */
  private[lagom] def internalInitialBehaviour(snapshotState: Option[State]): Behavior

  /**
   * Current state of the entity. Typically accessed from event and command handlers.
   */
  protected final def state: State = _behavior.state

  /**
   * Abstract method that must be implemented by concrete subclass to define
   * the behavior of the entity. Use [[#newBehaviorBuilder]] to create a mutable builder
   * for defining the behavior.
   */
  def initialBehavior(snapshotState: Option[State]): Behavior

  /**
   * Create a new empty `Behavior` with a given state.
   */
  //todo: implement partial functions
  final def newBehavior(state: State): Behavior = new Behavior(state, Map.empty, (c: Command, ctx: CoreCommandContext[Any]) => ???)

  /**
   * This method is called to notify the entity that the recovery process
   * is finished.
   */
  def recoveryCompleted(): Behavior = _behavior

  private class SimpleCoreCommandContext(replyTo: ActorRef) extends CoreCommandContext[Any] {
    def reply(msg: Any): Unit =
      replyTo ! msg

    override def commandFailed(cause: Throwable): Unit =
      // not using akka.actor.Status.Failure because it is using Java serialization
      reply(cause)
  }

  /**
   * INTERNAL API
   */
  protected[lagom] def newCtx(replyTo: ActorRef): CoreCommandContext[Any]

  /**
   * Behavior consists of current state and functions to process incoming commands
   * and persisted events. `Behavior` is an immutable class. Use the mutable [[BehaviorBuilder]]
   * for defining command and event handlers.
   */
  case class Behavior(
    state: State,

    //                       eventHandlers: Map[Class[_ <: Event], JFunction[_ <: Event, Behavior]],
    eventHandler:   Function[Event, Option[Behavior]],
    commandHandler: (Command, CoreCommandContext[Any]) => Option[Persist[_ <: Event]]

  //                       commandHandlers: Map[Class[_ <: Command], JBiFunction[_ <: Command, CoreCommandContext[Any], Persist[_ <: Event]]]
  //State    eventHandlers:   PartialFunction[_ <: Event, Behavior],
  //    BehaviorcommandHandlers: PartialFunction[_ <: Command, Function[CoreCommandContext[Any], Persist[_ <: Event]]]
  ) {

    /**
     * @return new instance with the given state
     */
    def withState(newState: State): Behavior =
      copy(state = newState)

    def transformState(f: State => State): Behavior =
      copy(state = f(state))
  }

  //here here here
  trait CoreCommandContext[T] {
    /**
     * Reply with a negative acknowledgment.
     */
    def commandFailed(cause: Throwable): Unit
  }

  /**
   * The context that is passed to read-only command handlers.
   */
  abstract class ReadOnlyCommandContext[R] extends CoreCommandContext[R] {

    /**
     * Send reply to a command. The type `R` must be the type defined by
     * the command.
     */
    def reply(msg: R): Unit

    /**
     * Reply with a negative acknowledgment using the standard
     * `InvalidCommandException`.
     */
    def invalidCommand(message: String): Unit =
      commandFailed(new CorePersistentEntity.InvalidCommandException(message))
  }

  /**
   * A command handler returns a `Persist` directive that defines what event or events,
   * if any, to persist. Use the `thenPersist`, `thenPersistAll` or `done` methods of the context
   * that is passed to the command handler function to create the `Persist` directive.
   */
  trait Persist[B <: Event]

  /**
   * INTERNAL API
   */
  private[lagom] case class PersistOne[B <: Event](val event: B, val afterPersist: Function[B, Unit]) extends Persist[B]

  /**
   * INTERNAL API
   */
  private[lagom] case class PersistAll[B <: Event](val events: immutable.Seq[B], val afterPersist: Function0[Unit]) extends Persist[B]

  /**
   * INTERNAL API
   */
  private[lagom] trait PersistNone[B <: Event] extends Persist[B] {
    override def toString: String = "PersistNone"
  }

  private[lagom] val persistNone = new PersistNone[Nothing] {}
}
