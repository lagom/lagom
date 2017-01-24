/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import akka.japi.Util.immutableSeq
import scala.collection.immutable
import java.util.function.{ BiFunction => JBiFunction }
import java.util.function.{ Consumer => JConsumer }
import java.util.function.{ BiConsumer => JBiConsumer }
import java.util.function.{ Function => JFunction }
import java.util.{ List => JList }
import java.util.Optional
import akka.event.LoggingAdapter
import scala.annotation.varargs
import scala.util.control.NoStackTrace
import scala.annotation.tailrec
import akka.japi.Effect
import akka.event.Logging

object PersistentEntity {
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
 * Type parameter `Command`:
 *   the super type of all commands, must implement [[PersistentEntity.ReplyType]]
 *   to define the reply type of each command type
 * Type parameter `Event`:
 *   the super type of all events
 * Type parameter `State`:
 *   the type of the state
 */
abstract class PersistentEntity[Command, Event, State] {
  import PersistentEntity.ReplyType

  private var _entityId: String = _

  final protected def entityId: String = _entityId

  /**
   * INTERNAL API
   */
  private[lagom] def internalSetEntityId(id: String) = _entityId = id

  private var _behavior: Behavior = _

  final def behavior: Behavior = _behavior

  /**
   * INTERNAL API
   */
  private[lagom] def internalSetCurrentBehavior(b: Behavior) = _behavior = b

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
  def initialBehavior(snapshotState: Optional[State]): Behavior

  /**
   * This method is called to notify the entity that the recovery process
   * is finished.
   */
  def recoveryCompleted(): Behavior = _behavior

  /**
   * Current state of the entity. Typically accessed from event and command handlers.
   */
  protected final def state: State = _behavior.state

  /**
   * Create a new empty `Behavior` with a given state.
   */
  final def newBehavior(state: State): Behavior = new Behavior(state, Map.empty, Map.empty)

  /**
   * Create a new empty `BehaviorBuilder` with a given state.
   */
  final def newBehaviorBuilder(state: State): BehaviorBuilder = new BehaviorBuilder(state)

  /**
   * Behavior consists of current state and functions to process incoming commands
   * and persisted events. `Behavior` is an immutable class. Use the mutable [[BehaviorBuilder]]
   * for defining command and event handlers.
   */
  case class Behavior(
    state:           State,
    eventHandlers:   Map[Class[_ <: Event], JFunction[_ <: Event, Behavior]],
    commandHandlers: Map[Class[_ <: Command], JBiFunction[_ <: Command, CommandContext[Any], Persist[_ <: Event]]]
  ) {

    /**
     * @return new instance with the given state
     */
    def withState(newState: State): Behavior =
      copy(state = newState)

    /**
     * Create a `BehaviorBuilder` that corresponds to this `Behavior`, i.e. the builder
     * is populated with same state, same event and command handler functions.
     */
    def builder(): BehaviorBuilder = new BehaviorBuilder(state, eventHandlers, commandHandlers)

  }

  /**
   * Mutable builder that is used for defining the event and command handlers.
   * Use [#build] to create the immutable [[Behavior]].
   */
  protected final class BehaviorBuilder(
    state:       State,
    evtHandlers: Map[Class[_ <: Event], JFunction[_ <: Event, Behavior]],
    cmdHandlers: Map[Class[_ <: Command], JBiFunction[_ <: Command, CommandContext[Any], Persist[_ <: Event]]]
  ) {

    def this(state: State) = this(state, Map.empty, Map.empty)

    private var _state = state
    private var eventHandlers: Map[Class[_ <: Event], JFunction[_ <: Event, Behavior]] = evtHandlers
    private var commandHandlers: Map[Class[_ <: Command], JBiFunction[_ <: Command, CommandContext[Any], Persist[_ <: Event]]] =
      cmdHandlers

    def getState(): State = _state

    def setState(state: State): Unit = {
      _state = state
    }

    /**
     * Register an event handler for a given event class. The `handler` function
     * is supposed to return the new state after applying the event to the current state.
     * Current state can be accessed with the `state` method of the `PersistentEntity`.
     */
    def setEventHandler[A <: Event](eventClass: Class[A], handler: JFunction[A, State]): Unit =
      setEventHandlerChangingBehavior[A](eventClass, new JFunction[A, Behavior] {
        override def apply(evt: A): Behavior = behavior.withState(handler.apply(evt))
      })

    /**
     * Register an event handler that is updating the behavior for a given event class.
     * The `handler` function  is supposed to return the new behavior after applying the
     * event to the current state. Current behavior can be accessed with the `behavior`
     * method of the `PersistentEntity`.
     */
    def setEventHandlerChangingBehavior[A <: Event](eventClass: Class[A], handler: JFunction[A, Behavior]): Unit =
      eventHandlers = eventHandlers.updated(eventClass, handler)

    /**
     * Remove an event handler for a given event class.
     */
    def removeEventHandler(eventClass: Class[_ <: Event]): Unit =
      eventHandlers -= eventClass

    /**
     * Register a command handler for a given command class.
     *
     * The `handler` function is supposed to return a `Persist` directive that defines
     * what event or events, if any, to persist. Use the `thenPersist`, `thenPersistAll`
     * or `done`  methods of the context that is passed to the handler function to create the
     *  `Persist` directive.
     *
     * After persisting an event external side effects can be performed in the `afterPersist`
     * function that can be defined when creating the `Persist` directive.
     * A typical side effect is to reply to the request to confirm that it was performed
     * successfully. Replies are sent with the `reply` method of the context that is passed
     * to the command handler function.
     *
     * The `handler` function may validate the incoming command and reject it by
     * sending a `reply` and returning `ctx.done()`.
     */
    def setCommandHandler[R, A <: Command with ReplyType[R]](
      commandClass: Class[A],
      handler:      JBiFunction[A, CommandContext[R], Persist[_ <: Event]]
    ): Unit = {
      commandHandlers = commandHandlers.updated(
        commandClass,
        handler.asInstanceOf[JBiFunction[A, CommandContext[Any], Persist[_ <: Event]]]
      )
    }

    /**
     * Remove a command handler for a given command class.
     */
    def removeCommandHandler(commandClass: Class[_ <: Command]): Unit =
      commandHandlers -= commandClass

    /**
     *  Register a read-only command handler for a given command class. A read-only command
     *  handler does not persist events (i.e. it does not change state) but it may perform side
     *  effects, such as replying to the request. Replies are sent with the `reply` method of the
     *  context that is passed to the command handler function.
     */
    def setReadOnlyCommandHandler[R, A <: Command with ReplyType[R]](
      commandClass: Class[A],
      handler:      JBiConsumer[A, ReadOnlyCommandContext[R]]
    ): Unit = {
      setCommandHandler[R, A](commandClass, new JBiFunction[A, CommandContext[R], Persist[_ <: Event]] {
        override def apply(cmd: A, ctx: CommandContext[R]): Persist[Event] = {
          handler.accept(cmd, ctx)
          ctx.done()
        }
      });
    }

    /**
     * Construct the corresponding immutable `Behavior`.
     */
    def build(): Behavior =
      Behavior(_state, eventHandlers, commandHandlers)

  }

  /**
   * The context that is passed to read-only command handlers.
   */
  abstract class ReadOnlyCommandContext[R] {

    /**
     * Send reply to a command. The type `R` must be the type defined by
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
   * The context that is passed to command handler function.
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
    def thenPersist[B <: Event](event: B, afterPersist: JConsumer[B]): Persist[B] =
      return new PersistOne(event, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted.
     */
    def thenPersistAll[B <: Event](events: JList[B]): Persist[B] =
      return new PersistAll(immutableSeq(events), afterPersist = null)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    def thenPersistAll[B <: Event](events: JList[B], afterPersist: Effect): Persist[B] =
      return new PersistAll(immutableSeq(events), afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    @varargs
    def thenPersistAll[B <: Event](afterPersist: Effect, events: B*): Persist[B] =
      return new PersistAll(events.toList, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that no events are to be persisted.
     */
    def done[B <: Event](): Persist[B] = persistNone.asInstanceOf[Persist[B]]

  }

  /**
   * A command handler returns a `Persist` directive that defines what event or events,
   * if any, to persist. Use the `thenPersist`, `thenPersistAll` or `done` methods of the context
   * that is passed to the command handler function to create the `Persist` directive.
   */
  abstract class Persist[B <: Event]

  /**
   * INTERNAL API
   */
  private[lagom] case class PersistOne[B <: Event](val event: B, val afterPersist: JConsumer[B]) extends Persist[B]

  /**
   * INTERNAL API
   */
  private[lagom] case class PersistAll[B <: Event](val events: immutable.Seq[B], val afterPersist: Effect) extends Persist[B]

  /**
   * INTERNAL API
   */
  private[lagom] trait PersistNone[B <: Event] extends Persist[B] {
    override def toString: String = "PersistNone"
  }

  private val persistNone = new PersistNone[Nothing] {}

}
