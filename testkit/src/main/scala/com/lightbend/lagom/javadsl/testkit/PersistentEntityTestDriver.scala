/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import java.util.function.{ BiFunction => JBiFunction, Function => JFunction }
import java.util.{ List => JList }

import akka.actor.ActorSystem
import akka.serialization.{ JavaSerializer, SerializationExtension, SerializerWithStringManifest }
import com.lightbend.lagom.javadsl.persistence.PersistentEntity

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

object PersistentEntityTestDriver {
  final case class Outcome[E, S](
    events: JList[E], state: S,
    sideEffects: JList[SideEffect],
    issues:      JList[Issue]
  ) {

    /**
     * The messages that were sent as replies using the context that is
     * passed as parameter to the command handler functions.
     */
    def getReplies: JList[Any] = sideEffects.asScala.collect { case Reply(msg) => msg }.asJava
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
 * A testing utility for verifying that a [[com.lightbend.lagom.javadsl.persistence.PersistentEntity]]
 * emits expected events and side-effects in response to incoming commands.
 *
 * It also verifies that all commands, events, replies and state are
 * serializable, and reports any such problems in the `issues` of the `Outcome`.
 */
class PersistentEntityTestDriver[C, E, S](system: ActorSystem, entity: PersistentEntity[C, E, S], entityId: String) {
  import PersistentEntityTestDriver._

  entity.internalSetEntityId(entityId)

  private val serialization = SerializationExtension(system)

  private var initialized = false
  private var sideEffects: Vector[SideEffect] = Vector.empty
  private var issues: Vector[Issue] = Vector.empty
  private var allIssues: Vector[Issue] = Vector.empty

  private val ctx: entity.CommandContext[Any] = new entity.CommandContext[Any] {
    override def reply(msg: Any): Unit = {
      sideEffects :+= Reply(msg)
      issues ++= checkSerialization(msg)
    }

    override def commandFailed(cause: Throwable): Unit =
      // not using akka.actor.Status.Failure because it is using Java serialization
      reply(cause)
  }

  private def eventHandlers: Map[Class[E], JFunction[E, entity.Behavior]] =
    entity.behavior.eventHandlers.asInstanceOf[Map[Class[E], JFunction[E, entity.Behavior]]]

  private def commandHandlers: Map[Class[C], JBiFunction[C, entity.CommandContext[Any], entity.Persist[E]]] =
    entity.behavior.commandHandlers.asInstanceOf[Map[Class[C], JBiFunction[C, entity.CommandContext[Any], entity.Persist[E]]]]

  /**
   * The entity will process the commands and the emitted events and side effects
   * are recorded and provided in the returned `Outcome`. Current state is also
   * included in the `Outcome`.
   *
   * `run` may be invoked multiple times to divide the sequence of commands
   * into manageable steps. The `Outcome` contains the events and side-effects of
   * the last `run`, but the state is not reset between different runs.
   */
  @varargs
  def run(commands: C*): Outcome[E, S] = {
    sideEffects = Vector.empty
    issues = Vector.empty

    if (!initialized) {
      initialized = true
      val inital = entity.initialBehavior(None)
      entity.internalSetCurrentBehavior(inital)

      val newBehavior = entity.recoveryCompleted()
      entity.internalSetCurrentBehavior(newBehavior)
    }

    var producedEvents: Vector[E] = Vector.empty
    commands.foreach { cmd =>
      issues ++= checkSerialization(cmd)
      commandHandlers.get(cmd.getClass.asInstanceOf[Class[C]]) match {
        case Some(handler) =>
          handler.apply(cmd.asInstanceOf[C], ctx) match {
            case _: entity.PersistNone[_] => // done
            case entity.PersistOne(event, afterPersist) =>
              issues ++= checkSerialization(event)
              try {
                producedEvents :+= event
                applyEvent(event)
                issues ++= checkSerialization(entity.behavior.state)
                if (afterPersist != null)
                  afterPersist(event)
              } catch {
                case NonFatal(e) =>
                  ctx.commandFailed(e) // reply with failure
                  throw e
              }
            case entity.PersistAll(events, afterPersist) =>
              var count = events.size
              events.foreach { evt =>
                issues ++= checkSerialization(evt)
                try {
                  producedEvents :+= evt
                  applyEvent(evt)
                  issues ++= checkSerialization(entity.behavior.state)
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

        case None =>
          issues :+= UnhandledCommand(cmd)
      }
    }

    allIssues ++= issues
    Outcome[E, S](producedEvents.asJava, entity.behavior.state, sideEffects.asJava, issues.asJava)
  }

  /**
   * Accumulated issues from all previous runs.
   */
  def getAllIssues: JList[Issue] = allIssues.asJava

  private def applyEvent(event: Any): Unit = {
    eventHandlers.get(event.getClass.asInstanceOf[Class[E]]) match {
      case Some(handler) =>
        val newBehavior = handler.apply(event.asInstanceOf[E])
        entity.internalSetCurrentBehavior(newBehavior)
      case None =>
        issues :+= UnhandledEvent(event)
    }
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
