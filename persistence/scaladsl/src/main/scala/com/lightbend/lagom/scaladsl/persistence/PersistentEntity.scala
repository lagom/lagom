/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.japi.Util.immutableSeq
import scala.collection.immutable
import java.util.function.{ BiFunction => JBiFunction }
import java.util.function.{ Consumer => JConsumer }
import java.util.function.{ BiConsumer => JBiConsumer }
import java.util.function.{ Function => JFunction }
import java.util.function.{ Supplier => JSupplier }
import java.util.{ List => JList }
import java.util.Optional
import akka.event.LoggingAdapter
import scala.annotation.varargs
import scala.annotation.tailrec
import scala.compat.java8.OptionConverters._
import scala.compat.java8.FunctionConverters._
import akka.actor.ActorRef
import akka.japi.Effect
import akka.event.Logging
import com.lightbend.lagom.persistence.CorePersistentEntity

object PersistentEntity {
  type ReplyType[R] = CorePersistentEntity.ReplyType[R]
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
abstract class PersistentEntity[Command, Event, State] extends CorePersistentEntity[Command, Event, State] {
  import CorePersistentEntity._

  /**
   * INTERNAL API
   */
  private[lagom] def internalInitialBehaviour(snapshotState: Option[State]) =
    initialBehavior(snapshotState)

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
   * Create a new empty `BehaviorBuilder` with a given state.
   */
  final def newBehaviorBuilder(state: State): BehaviorBuilder = new BehaviorBuilder(state)

  /**
   * Create a `BehaviorBuilder` that corresponds to current `Behavior`, i.e. the builder
   * is populated with same state, same event and command handler functions.
   */
  /*final def behaviorBuilder(): BehaviorBuilder =
    new BehaviorBuilder(
      state,
      behavior.eventHandlers,
      behavior.commandHandlers
    )*/

  override private[lagom] def newCtx(replyTo: ActorRef): CoreCommandContext[Any] = new CommandContext[Any] {

    def reply(msg: Any): Unit =
      replyTo ! msg

    override def commandFailed(cause: Throwable): Unit =
      // not using akka.actor.Status.Failure because it is using Java serialization
      reply(cause)

  }

  /**
   * Mutable builder that is used for defining the event and command handlers.
   * Use [#build] to create the immutable [[Behavior]].
   */
  protected final class BehaviorBuilder(
    val state:       State,
    val cmdHandlers: PartialFunction[_ <: Command, Function[CommandContext[Any], Persist[_ <: Event]]] = Map.empty,
    val evtHandlers: PartialFunction[_ <: Event, Behavior]                                             = PartialFunction.empty
  ) {

    // TODO apidocs
    def withState(state: State): BehaviorBuilder =
      copy(state = state)

    // TODO apidocs
    def withEventHandlers(evtHandlers: PartialFunction[Any, Behavior]): BehaviorBuilder =
      copy(evtHandlers = evtHandlers)

    // TODO apidocs
    def withCommandHandlers(cmdHandlers: PartialFunction[Any, Function[CommandContext[Any], Persist[_ <: Event]]]): BehaviorBuilder =
      copy(cmdHandlers = cmdHandlers)

    private def copy(
      state:       State                                                                             = state,
      evtHandlers: PartialFunction[_ <: Event, Behavior]                                             = evtHandlers,
      cmdHandlers: PartialFunction[_ <: Command, Function[CommandContext[Any], Persist[_ <: Event]]] = cmdHandlers
    ) =
      new BehaviorBuilder(state, cmdHandlers, evtHandlers)

    /**
     * Construct the corresponding immutable `Behavior`.
     */
    def build(): Behavior =
      Behavior(state, evtHandlers, cmdHandlers.asInstanceOf[PartialFunction[_ <: Command, Function[CoreCommandContext[Any], Persist[_ <: Event]]]])

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
      new PersistOne[B](event, afterPersist = null)

    /**
     * A command handler may return this `Persist` directive to define
     * that one event is to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     */
    def thenPersist[B <: Event](event: B, afterPersist: Function[B, Unit]): Persist[B] =
      new PersistOne[B](event, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted.
     */
    def thenPersistAll[B <: Event](events: immutable.Seq[B]): Persist[B] =
      return new PersistAll[B](events, afterPersist = null)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    def thenPersistAll[B <: Event](events: immutable.Seq[B], afterPersist: Function0[Unit]): Persist[B] =
      return new PersistAll[B](events, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    @varargs
    def thenPersistAll[B <: Event](afterPersist: Function0[Unit], events: B*): Persist[B] =
      return new PersistAll[B](events.toList, afterPersist)

    /**
     * A command handler may return this `Persist` directive to define
     * that no events are to be persisted.
     */
    def done[B <: Event](): Persist[B] = persistNone.asInstanceOf[Persist[B]]

  }

}
