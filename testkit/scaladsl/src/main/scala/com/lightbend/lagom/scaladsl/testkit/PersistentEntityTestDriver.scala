/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import scala.collection.immutable
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.Exception.Catcher
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.serialization.JavaSerializer
import akka.serialization.SerializationExtension
import akka.serialization.SerializerWithStringManifest
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

object PersistentEntityTestDriver {
  final case class Outcome[E, S](
    events: immutable.Seq[E], state: S,
    sideEffects: immutable.Seq[SideEffect],
    issues:      immutable.Seq[Issue]
  ) {

    /**
     * The messages that were sent as replies using the context that is
     * passed as parameter to the command handler functions.
     */
    def replies: immutable.Seq[Any] = sideEffects.collect { case Reply(msg) => msg }
  }

  trait SideEffect

  final case class Reply(msg: Any) extends SideEffect

  trait Issue

  final case class NoSerializer(obj: Any, cause: Throwable) extends Issue {
    override def toString: String = s"No serializer defined for ${obj.getClass}"
  }
  final case class UsingJavaSerializer(obj: Any) extends Issue {
    override def toString: String = s"Java serialization is used for ${obj.getClass}"
  }
  final case class NotSerializable(obj: Any, cause: Throwable) extends Issue {
    override def toString: String = s"${obj.getClass} is not serializable, ${cause.getMessage}"
  }
  final case class NotDeserializable(obj: Any, cause: Throwable) extends Issue {
    override def toString: String = s"${obj.getClass} is not deserializable, ${cause.getMessage}"
  }
  final case class NotEqualAfterSerialization(message: String, objBefore: AnyRef, objAfter: AnyRef) extends Issue {
    override def toString: String = message
  }
  final case class UnhandledCommand(command: Any) extends Issue {
    override def toString: String = s"No command handler registered for ${command.getClass}"
  }
  final case class UnhandledEvent(event: Any) extends Issue {
    override def toString: String = s"No event handler registered for ${event.getClass}"
  }

}

/**
 * A testing utility for verifying that a [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]]
 * emits expected events and side-effects in response to incoming commands.
 *
 * It also verifies that all commands, events, replies and state are
 * serializable, and reports any such problems in the `issues` of the `Outcome`.
 */
class PersistentEntityTestDriver[C, E, S](
  val system:   ActorSystem,
  val entity:   PersistentEntity { type Command = C; type Event = E; type State = S },
  val entityId: String
) {
  import PersistentEntityTestDriver._

  private val serialization = SerializationExtension(system)

  entity.internalSetEntityId(entityId)
  private var state: S = entity.initialState
  private val behavior: entity.Behavior = entity.behavior

  private var initialized = false
  private var sideEffects: Vector[SideEffect] = Vector.empty
  private var issues: Vector[Issue] = Vector.empty
  private var allIssues: Vector[Issue] = Vector.empty

  /**
   * Initialize the entity.
   *
   * This can be used to simulate the startup of an entity, be passing some snapshot state, and then some additional
   * events that weren't included in that entity.
   *
   * The returned outcome contains the state with events applied to it and any issues with the state and events.
   *
   * This method may not be invoked twice, and it also must not be invoked after passing commands to the `run` method,
   * since that will automatically initialize the entity with an empty snapshot if not yet initialized.
   *
   * @param snapshotState The state to initialize the entity with.
   * @param events The additional events to run before invoking `recoveryCompleted`
   * @return The outcome.
   */
  def initialize(snapshotState: Option[S], events: E*): Outcome[E, S] = {
    if (initialized) {
      throw new IllegalStateException("The entity has already been initialized")
    }
    initialized = true

    issues = Vector.empty

    snapshotState.foreach(state = _)
    issues ++= checkSerialization(state)

    events.foreach { event =>
      issues ++= checkSerialization(event)
      applyEvent(event)
      issues ++= checkSerialization(state)
    }

    state = entity.recoveryCompleted(state)
    issues ++= checkSerialization(state)

    allIssues ++= issues
    Outcome(events.toVector, state, Nil, issues)
  }

  private val unhandledState: Catcher[Nothing] = {
    case e: MatchError ⇒ throw new IllegalStateException(
      s"Undefined state [${state.getClass.getName}] in [${entity.getClass.getName}] with id [${entityId}]"
    )
  }

  private def unhandledCommand: PartialFunction[(C, entity.CommandContext[Any], S), entity.Persist] = {
    case (cmd, _, _) =>
      issues :+= UnhandledCommand(cmd)
      entity.PersistNone
  }

  def runOne[CC <: entity.Command](command: C): Outcome[E, S] = ???

  /**
   * The entity will process the commands and the emitted events and side effects
   * are recorded and provided in the returned `Outcome`. Current state is also
   * included in the `Outcome`.
   *
   * `run` may be invoked multiple times to divide the sequence of commands
   * into manageable steps. The `Outcome` contains the events and side-effects of
   * the last `run`, but the state is not reset between different runs.
   */
  def run(commands: C*): Outcome[E, S] = {
    sideEffects = Vector.empty
    issues = Vector.empty

    if (!initialized) {
      initialize(None)
    }

    var producedEvents: Vector[E] = Vector.empty
    commands.foreach {
      case cmd: PersistentEntity.ReplyType[Any] @unchecked =>
        val ctx = new entity.CommandContext[Any] {
          override def reply(msg: Any): Unit = {
            sideEffects :+= Reply(msg)
            issues ++= checkSerialization(msg)
          }

          override def commandFailed(cause: Throwable): Unit = {
            // not using akka.actor.Status.Failure because it is using Java serialization
            sideEffects :+= Reply(cause)
            issues ++= checkSerialization(cause)
          }
        }

        issues ++= checkSerialization(cmd)

        val actions = try behavior(state) catch unhandledState
        val commandHandler = actions.commandHandlers.get(cmd.getClass) match {
          case Some(h) => h
          case None    => PartialFunction.empty
        }
        val result = commandHandler.applyOrElse((cmd.asInstanceOf[C], ctx, state), unhandledCommand)

        result match {
          case entity.PersistNone => // done
          case entity.PersistOne(event, afterPersist) =>
            issues ++= checkSerialization(event)
            try {
              val evt = event.asInstanceOf[E]
              producedEvents :+= evt
              applyEvent(evt)
              issues ++= checkSerialization(state)
              if (afterPersist != null)
                afterPersist(event)
            } catch {
              case NonFatal(e) =>
                ctx.commandFailed(e) // reply with failure
                throw e
            }
          case entity.PersistAll(events, afterPersist) =>
            var count = events.size
            events.foreach { event =>
              val evt = event.asInstanceOf[E]
              issues ++= checkSerialization(evt)
              try {
                producedEvents :+= evt
                applyEvent(evt)
                issues ++= checkSerialization(state)
                count -= 1
                if (afterPersist != null && count == 0)
                  afterPersist.apply()
              } catch {
                case NonFatal(e) =>
                  ctx.commandFailed(e) // reply with failure
                  throw e
              }
            }
        }

      case otherCommandType =>
        // didn't implement PersistentEntity.ReplyType
        issues :+= UnhandledCommand(otherCommandType)
    }

    allIssues ++= issues
    Outcome[E, S](producedEvents, state, sideEffects, issues)
  }

  /**
   * Accumulated issues from all previous runs.
   */
  def getAllIssues: immutable.Seq[Issue] = allIssues

  private val unhandledEvent: PartialFunction[(E, S), S] = {
    case event =>
      issues :+= UnhandledEvent(event)
      state
  }

  private def applyEvent(event: E): Unit = {
    val actions = try behavior(state) catch unhandledState
    state = actions.eventHandler.applyOrElse((event, state), unhandledEvent)
  }

  private def checkSerialization(obj: Any): Option[Issue] = {
    val obj1 = obj.asInstanceOf[AnyRef]
    // check that it is configured
    Try(serialization.serializerFor(obj.getClass)) match {
      case Failure(e) => Some(NoSerializer(obj, e))
      case Success(serializer) =>
        // verify serialization-deserialization round trip
        Try(serializer.toBinary(obj1)) match {
          case Failure(e) => Some(NotSerializable(obj, e))
          case Success(blob) =>
            val manifest = serializer match {
              case ser: SerializerWithStringManifest => ser.manifest(obj1)
              case _                                 => if (serializer.includeManifest) obj.getClass.getName else ""
            }
            serialization.deserialize(blob, serializer.identifier, manifest) match {
              case Failure(e) => Some(NotDeserializable(obj, e))
              case Success(obj2) =>
                if (obj != obj2) {
                  Some(NotEqualAfterSerialization(
                    s"Object [$obj] does not equal [$obj2] after serialization/deserialization", obj1, obj2
                  ))
                } else if (serializer.isInstanceOf[JavaSerializer] && !isOkForJavaSerialization(obj1.getClass))
                  Some(UsingJavaSerializer(obj1))
                else
                  None
            }
        }
    }
  }

  private def isOkForJavaSerialization(clazz: Class[_]): Boolean = {
    // e.g. String
    clazz.getName.startsWith("java.lang.") ||
      clazz.getName.startsWith("akka.")
  }

}
