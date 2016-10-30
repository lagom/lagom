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
import scala.reflect.ClassTag

object PersistentEntity {
  /**
   * Commands to a `PersistentEntity` must implement this interface
   * to define the reply type.
   *
   * `akka.Done` can optionally be used as a "standard" acknowledgment message.
   *
   * @tparam R the type of the reply message
   */
  trait ReplyType[R] {
    type ReplyType = R
  }

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
 * `initialState` and `behavior` are abstract methods that your concrete subclass must implement.
 * The behavior is defined as a set of actions given a state. The actions are functions to process
 * incoming commands and persisted events.
 *
 * The `PersistentEntity` receives commands of type `Command` that can be validated before
 * persisting state changes as events of type `Event`. The functions that process incoming
 * commands are registered in the `Actions` using `onCommand` of the
 * `Actions`.
 *
 * A command may also be read-only and only perform some side-effect, such as replying
 * to the request. Such command handlers are registered using `onReadOnlyCommand`
 * of the `Actions`. Replies are sent with the `reply` method of the context that
 * is passed to the command handler function.
 *
 * A command handler returns a `Persist` directive that defines what event or events,
 * if any, to persist. Use the `thenPersist`, `thenPersistAll` or `done` methods of the
 * context that is passed to the command handler function to create the `Persist` directive.
 *
 * When an event has been persisted successfully the state of type `State` is updated by
 * applying the event to the current state. The functions for updating the state are
 * registered with the `onEvent` method of the `Actions`.
 * The event handler returns the new state. The state must be immutable, so you return
 * a new instance of the state. Current state is passed as parameter to the event handler.
 * The same event handlers are also used when the entity is started up to recover its
 * state from the stored events.
 *
 * After persisting an event, external side effects can be performed in the `afterPersist`
 * function that can be defined when creating the `Persist` directive.
 * A typical side effect is to reply to the request to confirm that it was performed
 * successfully. Replies are sent with the `reply` method of the context that is passed
 * to the command handler function.
 *
 * The event handlers are typically only updating the state, but they may also change
 * the behavior of the entity in the sense that new functions for processing commands
 * and events may be defined for a given state. This is useful when implementing
 * finite state machine (FSM) like entities.
 *
 * When the entity is started the state is recovered by replaying stored events.
 * To reduce this recovery time the entity may start the recovery from a snapshot
 * of the state and then only replaying the events that were stored after the snapshot.
 * Such snapshots are automatically saved after a configured number of persisted events.
 *
 * @tparam Command the super type of all commands, must implement [[PersistentEntity.ReplyType]]
 *   to define the reply type of each command type
 * @tparam Event the super type of all events
 * @tparam State the type of the state
 */
abstract class PersistentEntity {

  type Command
  type Event
  type State

  type Behavior = State => Actions
  type EventHandler = PartialFunction[(Event, State), State]
  private[lagom]type CommandHandler = PartialFunction[(Command, CommandContext[Any], State), Persist[Event]]
  private[lagom]type ReadOnlyCommandHandler = PartialFunction[(Command, ReadOnlyCommandContext[Any], State), Unit]

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

  def initialState: State

  /**
   * Abstract method that must be implemented by concrete subclass to define
   * the behavior of the entity.
   */
  def behavior: Behavior

  /**
   * This method is called to notify the entity that the recovery process
   * is finished.
   */
  def recoveryCompleted(state: State): State = state

  object Actions {
    private val empty = new Actions(PartialFunction.empty, Map.empty)
    def apply(): Actions = empty
  }

  /**
   * Actions consists of functions to process incoming commands
   * and persisted events. `Actions` is an immutable class.
   */
  class Actions(
    val eventHandler:    EventHandler,
    val commandHandlers: Map[Class[_], CommandHandler]
  ) extends Function1[State, Actions] {

    /**
     * Extends `State => Actions` so that it can be used directly in
     * [[PersistentEntity#behavior]] when there is only one set of actions
     * independent of state.
     */
    def apply(state: State): Actions = this

    /**
     * Add a command handler. For each command class the handler is a
     * `PartialFunction`. Adding a handler for a command class that was
     * previously defined will replace the previous handler for that class.
     * It is possible to combine handlers from two different `Actions` with
     * [[#orElse]] method.
     */
    def onCommand[C <: Command with PersistentEntity.ReplyType[Reply]: ClassTag, Reply](
      handler: PartialFunction[(Command, CommandContext[Reply], State), Persist[Event]]
    ): Actions = {
      val commandClass = implicitly[ClassTag[C]].runtimeClass.asInstanceOf[Class[C]]
      new Actions(eventHandler, commandHandlers.updated(commandClass, handler.asInstanceOf[CommandHandler]))
    }

    /**
     * Add a command handler that will not persist any events. This is a convenience
     * method to [[#onCommand]]. For each command class the handler is a
     * `PartialFunction`. Adding a handler for a command class that was
     * previously defined will replace the previous handler for that class.
     * It is possible to combine handlers from two different `Actions` with
     * [[#orElse]] method.
     */
    def onReadOnlyCommand[C <: Command with PersistentEntity.ReplyType[Reply]: ClassTag, Reply](
      handler: PartialFunction[(Command, ReadOnlyCommandContext[Reply], State), Unit]
    ): Actions = {
      val delegate: PartialFunction[(Command, CommandContext[Reply], State), Persist[Event]] = {
        case params @ (_, ctx, _) if handler.isDefinedAt(params) =>
          handler(params)
          ctx.done
      }
      onCommand[C, Reply](delegate)
    }

    /**
     * Add an event handler. Each handler is a `PartialFunction` and they
     * will be tried in the order they were added, i.e. they are combined
     * with `orElse`.
     */
    def onEvent(handler: EventHandler): Actions = {
      new Actions(eventHandler.orElse(handler), commandHandlers)
    }

    /**
     * Append `eventHandler` and `commandHandlers` from `b` to the handlers
     * of this `Actions`.
     *
     * Event handlers are combined with `orElse` of the partial functions.
     *
     * Command handlers for a specific command class that are defined in
     * both `b` and this `Actions` will be combined with `orElse` of the
     * partial functions.
     */
    def orElse(b: Actions): Actions = {
      val commandsInBoth = commandHandlers.keySet intersect b.commandHandlers.keySet
      val newCommandHandlers = commandHandlers ++ b.commandHandlers ++
        commandsInBoth.map(c => c -> commandHandlers(c).orElse(b.commandHandlers(c)))
      new Actions(eventHandler.orElse(b.eventHandler), newCommandHandlers)
    }

  }

  /**
   * The context that is used by read-only command handlers.
   * Replies are sent with the context.
   *
   * @tparam R the reply type of the command
   */
  abstract class ReadOnlyCommandContext[R] {

    /**
     * Send reply to a command. The type `R` must be the reply type defined by
     * the command.
     */
    def reply(msg: R): Unit

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
   *
   * @tparam R the reply type of the command
   */
  abstract class CommandContext[R] extends ReadOnlyCommandContext[R] {

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

