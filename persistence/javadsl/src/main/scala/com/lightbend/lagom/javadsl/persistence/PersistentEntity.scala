/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

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
 * the super type of all commands, must implement [[PersistentEntity.ReplyType]]
 * to define the reply type of each command type
 * Type parameter `Event`:
 * the super type of all events
 * Type parameter `State`:
 * the type of the state
 */
abstract class PersistentEntity[Command, Event, State] extends CorePersistentEntity[Command, Event, State] {

  import CorePersistentEntity._

  /**
   * INTERNAL API
   */
  override protected[lagom] def newCtx(replyTo: ActorRef): CoreCommandContext[Any] = new CommandContext[Any] {

    override def reply(msg: Any): Unit =
      replyTo ! msg

    override def commandFailed(cause: Throwable): Unit =
      // not using akka.actor.Status.Failure because it is using Java serialization
      reply(cause)
  }

  /**
   * INTERNAL API
   */
  private[lagom] def internalInitialBehaviour(snapshotState: Option[State]) =
    initialBehavior(snapshotState.asJava)

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

  final def initialBehavior(snapshotState: Option[State]): Behavior =
    initialBehavior(snapshotState.asJava)

  /**
   * Create a new empty `BehaviorBuilder` with a given state.
   */
  final def newBehaviorBuilder(state: State): BehaviorBuilder = new BehaviorBuilder(state)

  final def newBehaviorBuilder(behavior: Behavior): BehaviorBuilder = new BehaviorBuilder(behavior)

  /**
   * Create a `BehaviorBuilder` that corresponds to current `Behavior`, i.e. the builder
   * is populated with same state, same event and command handler functions.
   */
  final def behaviorBuilder(): BehaviorBuilder =
    new BehaviorBuilder(state, behavior)

  /**
   * Mutable builder that is used for defining the event and command handlers.
   * Use [#build] to create the immutable [[Behavior]].
   */
  protected final class BehaviorBuilder(
    state:            State,
    evtHandlers:      Map[Class[_ <: Event], JFunction[Event, Behavior]],
    cmdHandlers:      Map[Class[_ <: Command], JBiFunction[Command, CoreCommandContext[Any], Persist[_ <: Event]]],
    previousBehavior: Option[Behavior]
  ) {

    def this(state: State) = this(state, Map.empty, Map.empty, None)

    def this(state: State, previousBehavior: Behavior) = this(state, Map.empty, Map.empty, Option(previousBehavior))

    def this(previousBehavior: Behavior) = this(previousBehavior.state, Map.empty, Map.empty, Option(previousBehavior))

    private var _state = state
    private var eventHandlers: Map[Class[_ <: Event], (Event) => Behavior] = evtHandlers.map(a => a._1 -> a._2.asScala).toMap
    private var commandHandlers: Map[Class[_ <: Command], JBiFunction[Command, CoreCommandContext[Any], Persist[_ <: Event]]] =
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
      setEventHandlerChangingBehavior(eventClass, new JFunction[A, Behavior] {
        override def apply(evt: A): Behavior = behavior.withState(handler.apply(evt))
      })

    /**
     * Register an event handler that is updating the behavior for a given event class.
     * The `handler` function  is supposed to return the new behavior after applying the
     * event to the current state. Current behavior can be accessed with the `behavior`
     * method of the `PersistentEntity`.
     */
    def setEventHandlerChangingBehavior[A <: Event](eventClass: Class[A], handler: JFunction[A, Behavior]): Unit =
      eventHandlers = eventHandlers.updated(eventClass, handler.asScala.asInstanceOf[Function[Event, Behavior]])

    /**
     * Remove an event handler for a given event class.
     */
    def removeEventHandler(eventClass: Class[_ <: Event]): Unit = ()

    //todo: implement remove
    //      eventHandlers -= eventClass

    /**
     * Register a command handler for a given command class.
     *
     * The `handler` function is supposed to return a `Persist` directive that defines
     * what event or events, if any, to persist. Use the `thenPersist`, `thenPersistAll`
     * or `done`  methods of the context that is passed to the handler function to create the
     * `Persist` directive.
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
    def setCommandHandler[R, A <: Command with ReplyType[R], CTX <: CoreCommandContext[R]](
      commandClass: Class[A],
      handler:      JBiFunction[A, CTX, Persist[_ <: Event]]
    ): Unit = {
      commandHandlers = commandHandlers.updated(
        commandClass,
        handler.asInstanceOf[JBiFunction[Command, CoreCommandContext[Any], Persist[_ <: Event]]]
      )
    }

    /**
      * Register a read-only command handler for a given command class. A read-only command
      * handler does not persist events (i.e. it does not change state) but it may perform side
      * effects, such as replying to the request. Replies are sent with the `reply` method of the
      * context that is passed to the command handler function.
      */
    def setReadOnlyCommandHandler[R, A <: Command with ReplyType[R], CTX <: CommandContext[R]](
                                                                      commandClass: Class[A],
                                                                      handler:      JBiConsumer[A, CTX]
                                                                    ): Unit = {
      setCommandHandler[R, A, CTX](commandClass, new JBiFunction[A, CTX, Persist[_ <: Event]] {
        override def apply(cmd: A, ctx: CTX): Persist[Event] = {
          handler.accept(cmd, ctx)
          ctx.done()
        }
      });
      print(commandHandlers)
    }


    /**
     * Remove a command handler for a given command class.
     */
    def removeCommandHandler(commandClass: Class[_ <: Command]): Unit =
      commandHandlers -= commandClass


    /**
     * Construct the corresponding immutable `Behavior`.
     */
    def build(): Behavior = {
      //todo: implement build
      //      val evtHandlersPf = evtHandlers.values
      //        .map(_.asScala.asInstanceOf[PartialFunction[Event, Behavior]])
      //        .foldLeft(previousBehavior.map(_.eventHandlers).getOrElse(PartialFunction.empty[Event, Behavior]))(_ orElse _)
      //      val cmdHandlersPf = cmdHandlers.values
      //        .map(_.asScala.curried.asInstanceOf[PartialFunction[Command, Function[CoreCommandContext[Any], Persist[Event]]]])
      //        .foldLeft(previousBehavior.map(_.commdHandlers).getOrElse(PartialFunction.empty[Command, Function[CoreCommandContext[Any], Persist[Event]]]))(_ orElse _)


      def handler(cmd: Command, ctx: CoreCommandContext[Any]): Option[Persist[_ <: Event]] = {
        Some(commandHandlers.get(cmd.getClass).map(a => a.apply(cmd, ctx)).getOrElse(persistNone))
      }

      def eventHandler(event: Event): Option[Behavior] = {
        eventHandlers.get(event.getClass).map(_.apply(event))
      }

      Behavior(_state, eventHandler, handler)
    }

  }

  /**
   * The context that is passed to command handler function.
   */
  //here here here
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
    def thenPersist[B <: Event](event: B, afterPersist: JConsumer[B]): Persist[B] =
      new PersistOne[B](event, afterPersist.asScala)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted.
     */
    def thenPersistAll[B <: Event](events: JList[B]): Persist[B] =
      return new PersistAll[B](immutableSeq(events), afterPersist = null)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    def thenPersistAll[B <: Event](events: JList[B], afterPersist: Effect): Persist[B] =
      return new PersistAll[B](immutableSeq(events), afterPersist.asScala)

    /**
     * A command handler may return this `Persist` directive to define
     * that several events are to be persisted. External side effects can be
     * performed after successful persist in the `afterPersist` function.
     * `afterPersist` is invoked once when all events have been persisted
     * successfully.
     */
    @varargs
    def thenPersistAll[B <: Event](afterPersist: Effect, events: B*): Persist[B] =
      return new PersistAll[B](events.toList, afterPersist.asScala)

    /**
     * A command handler may return this `Persist` directive to define
     * that no events are to be persisted.
     */
    def done[B <: Event](): Persist[B] = persistNone.asInstanceOf[Persist[B]]

  }

}
