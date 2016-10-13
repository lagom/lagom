/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import scala.collection.immutable
import akka.event.LoggingAdapter
import scala.util.control.NoStackTrace
import scala.annotation.tailrec
import akka.event.Logging
import akka.actor.ActorRef

object PersistentEntity {
  /**
   * Commands to a `PersistentEntity` must implement this interface
   * to define the reply type.
   *
   * `akka.Done` can optionally be used as a "standard" acknowledgment message.
   *
   * Type parameter `R` is the type of the reply message.
   */
  trait ReplyType[-R]

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
 * A `PersistentEntity` has a stable entity identifier, with which
 * it can be accessed from anywhere in the cluster. It is run by an actor
 * and the state is persistent using event sourcing.
 *
 * `initialBehavior` is an abstract method that your concrete subclass must implement.
 * It returns the `Behavior` of the entity. The behavior consists of current state
 * and functions to process incoming commands and persisted events.
 *
 * The `PersistentEntity` receives commands of type `Command` that can be validated before
 * persisting state changes as events of type `Event`. The functions that process incoming
 * commands are registered in the `Behavior` using `setCommandHandler` of the
 * `BehaviorBuilder`.
 *
 * A command may also be read-only and only perform some side-effect, such as replying
 * to the request. Such command handlers are registered using `setReadOnlyCommandHandler`
 * of the `BehaviorBuilder`. Replies are sent with the `reply` method of the context that
 * is passed to the command handler function.
 *
 * A command handler returns a `Persist` directive that defines what event or events,
 * if any, to persist. Use the `thenPersist`, `thenPersistAll` or `done` methods of the
 * context that is passed to the command handler function to create the `Persist` directive.
 *
 * When an event has been persisted successfully the state of type `State` is updated by
 * applying the event to the current state. The functions for updating the state are
 * registered with the `setEventHandler` method of the `BehaviorBuilder`.
 * The event handler returns the new state. The state must be immutable, so you return
 * a new instance of the state. Current state can be accessed from the event handler with
 * the `state` method of the `PersistentEntity`. The same event handlers are also used when
 * the entity is started up to recover its state from the stored events.
 *
 * After persisting an event, external side effects can be performed in the `afterPersist`
 * function that can be defined when creating the `Persist` directive.
 * A typical side effect is to reply to the request to confirm that it was performed
 * successfully. Replies are sent with the `reply` method of the context that is passed
 * to the command handler function.
 *
 * The event handlers are typically only updating the state, but they may also change
 * the behavior of the entity in the sense that new functions for processing commands
 * and events may be defined. This is useful when implementing finite state machine (FSM)
 * like entities. Event handlers that change the behavior are registered with the
 * `setEventHandlerChangingBehavior` of the `BehaviorBuilder`. Such an event handler
 * returns the new `Behavior` instead of just returning the new state. You can
 * access current behavior with the `behavior` method of the `PersistentEntity`
 * and using the `builder` method of the `Behavior`.
 *
 * When the entity is started the state is recovered by replaying stored events.
 * To reduce this recovery time the entity may start the recovery from a snapshot
 * of the state and then only replaying the events that were stored after the snapshot.
 * Such snapshots are automatically saved after a configured number of persisted events.
 * The snapshot if any is passed as a parameter to the `initialBehavior` method and
 * you should use that state as the state of the returned `Behavior`.
 * One thing to keep in mind is that if you are using event handlers that change the
 * behavior (`setEventHandlerChangingBehavior`) you must also restore corresponding
 * `Behavior` from the snapshot state that is passed as a parameter to the `initialBehavior`
 * method.
 *
 * @tparam Command the super type of all commands, must implement [[PersistentEntity.ReplyType]]
 *   to define the reply type of each command type
 * @tparam Event the super type of all events
 * @tparam State the type of the state
 */
abstract class PersistentEntity[Command, Event, State] {
  import PersistentEntity.ReplyType

  private var _entityId: String = _

  final protected def entityId: String = _entityId

  /**
   * INTERNAL API
   */
  private[lagom] def internalSetEntityId(id: String) = _entityId = id

  /**
   * The name of this entity type. It should be unique among the entity
   * types of the service. By default it is using the short class name
   * of the concrete `PersistentEntity` class. Subclass may override
   * to define other type names. It is needed to override and retain
   * the original name when the class name is changed because this name
   * is part of the key of the store data (it is part of the `persistenceId`
   * of the underlying `PersistentActor`).
   */
  def entityTypeName: String = Logging.simpleName(getClass)

  /**
   * Abstract method that must be implemented by concrete subclass to define
   * the behavior of the entity. Use [[#newBehaviorBuilder]] to create a mutable builder
   * for defining the behavior.
   */
  def initialBehavior(snapshotState: Option[State]): Behavior

  /**
   * This method is called to notify the entity that the recovery process
   * is finished.
   */
  def recoveryCompleted(currentBehavior: Behavior): Behavior = currentBehavior

  type EventHandler = PartialFunction[(Event, Behavior), Behavior]
  type CommandHandler = PartialFunction[(Command, CommandContext, State), Persist[Event]]
  type ReadOnlyCommandHandler = PartialFunction[(Command, ReadOnlyCommandContext, State), Unit]

  object Behavior {
    def apply(state: State): Behavior =
      new Behavior(state, PartialFunction.empty, PartialFunction.empty)
  }

  /**
   * Behavior consists of current state and functions to process incoming commands
   * and persisted events. `Behavior` is an immutable class.
   */
  class Behavior(
    val state:          State,
    val eventHandler:   EventHandler,
    val commandHandler: CommandHandler
  ) {

    def addCommandHandler(handler: CommandHandler) = {
      new Behavior(state, eventHandler,
        commandHandler.orElse(handler))
    }

    def addReadOnlyCommandHandler(handler: ReadOnlyCommandHandler) = {
      val delegate: CommandHandler = {
        case params @ (_, ctx, _) if handler.isDefinedAt(params) =>
          handler(params)
          ctx.done
      }

      new Behavior(state, eventHandler, commandHandler.orElse(delegate))
    }

    def addEventHandler(handler: EventHandler) = {
      new Behavior(state, eventHandler.orElse(handler), commandHandler)
    }

    /**
     * @return new instance with the given state
     */
    def withState(newState: State): Behavior =
      new Behavior(state = newState, eventHandler, commandHandler)

    /**
     * Transform the state
     */
    def mapState(f: State => State): Behavior =
      new Behavior(state = f(state), eventHandler, commandHandler)

    /**
     * Append `eventHandler` and `commandHandler` from `b` to the handlers
     * of this `Behavior`.
     */
    def orElse(b: Behavior): Behavior =
      new Behavior(state, eventHandler.orElse(b.eventHandler), commandHandler.orElse(b.commandHandler))

  }

  /**
   * The context that is used by read-only command handlers.
   * Replies are sent with the context.
   */
  abstract class ReadOnlyCommandContext {

    /**
     * Send reply to a command. The type `R` must be the type defined by
     * the command.
     */
    def reply[R](currentCommand: Command with ReplyType[R], msg: R): Unit

    /**
     * Reply with a negative acknowledgment.
     */
    def commandFailed(cause: Throwable): Unit

    /**
     * Reply with a negative acknowledgment using the standard
     * `InvalidCommandException`.
     */
    def invalidCommand(message: String): Unit =
      commandFailed(new PersistentEntity.InvalidCommandException(message))
  }

  /**
   * The context that is used by command handler function.
   * Events are persisted with the context and replies are sent with the context.
   */
  abstract class CommandContext extends ReadOnlyCommandContext {

    /**
     * A command handler may return this `Persist` directive to define
     * that one event is to be persisted.
     */
    def thenPersist[B <: Event](event: B): Persist[B] =
      return new PersistOne(event, afterPersist = null)

    /**
     * A command handler may return this `Persist` directive to define
     * that one event is to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     */
    def thenPersist[B <: Event](event: B, afterPersist: Function1[B, Unit]): Persist[B] =
      return new PersistOne(event, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted.
     */
    def thenPersistAll[B <: Event](events: immutable.Seq[B]): Persist[B] =
      return new PersistAll(events, afterPersist = null)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    def thenPersistAll[B <: Event](events: immutable.Seq[B], afterPersist: Function0[Unit]): Persist[B] =
      return new PersistAll(events, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    def thenPersistAll[B <: Event](afterPersist: Function0[Unit], events: B*): Persist[B] =
      return new PersistAll(events.toList, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that no events are to be persisted.
     */
    def done[B <: Event]: Persist[B] = persistNone.asInstanceOf[Persist[B]]

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
  private[lagom] case class PersistOne[B <: Event](val event: B, val afterPersist: Function1[B, Unit]) extends Persist[B]

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

