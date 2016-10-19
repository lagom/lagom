/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import scala.util.control.Exception.Catcher
import java.net.URLDecoder

import scala.util.control.NonFatal
import akka.actor.ActorRef
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.RecoveryCompleted
import akka.persistence.SnapshotOffer
import akka.util.ByteString

import scala.concurrent.duration.FiniteDuration
import akka.actor.ReceiveTimeout
import akka.cluster.sharding.ShardRegion
import com.lightbend.lagom.scaladsl.persistence.{ AggregateEvent, AggregateEventShards, AggregateEventTag, PersistentEntity }
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

import akka.persistence.journal.Tagged
import play.api.Logger

private[lagom] object PersistentEntityActor {
  def props[C, E, S](
    persistenceIdPrefix:       String,
    entityId:                  Option[String],
    entityFactory:             () => PersistentEntity[C, E, S],
    snapshotAfter:             Option[Int],
    passivateAfterIdleTimeout: FiniteDuration
  ): Props =
    Props(new PersistentEntityActor(persistenceIdPrefix, entityId, entityFactory(), snapshotAfter.getOrElse(0),
      passivateAfterIdleTimeout))

  /**
   * Stop the actor for passivation. `PoisonPill` does not work well
   * with persistent actors.
   */
  case object Stop
}

/**
 * The `PersistentActor` that runs a [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]].
 */
private[lagom] class PersistentEntityActor[C, E, S](
  persistenceIdPrefix:       String,
  id:                        Option[String],
  entity:                    PersistentEntity[C, E, S],
  snapshotAfter:             Int,
  passivateAfterIdleTimeout: FiniteDuration
) extends PersistentActor {
  private val log = Logger(this.getClass)

  private val entityId: String = id.getOrElse(
    URLDecoder.decode(self.path.name, ByteString.UTF_8)
  )

  override val persistenceId: String = persistenceIdPrefix + entityId

  entity.internalSetEntityId(entityId)
  private var state: S = entity.initialState
  private val behavior: entity.Behavior = entity.behavior

  private var eventCount = 0L

  context.setReceiveTimeout(passivateAfterIdleTimeout)

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snapshot) =>
      state = snapshot.asInstanceOf[S]

    case RecoveryCompleted =>
      state = entity.recoveryCompleted(state)

    case evt =>
      applyEvent(evt.asInstanceOf[E])
      eventCount += 1
  }

  private val unhandledEvent: PartialFunction[(E, S), S] = {
    case event =>
      log.warn(s"Unhandled event [${event.getClass.getName}] in [${entity.getClass.getName}] with id [${entityId}]")
      state
  }

  private val unhandledState: Catcher[Nothing] = {
    case e: MatchError ⇒ throw new IllegalStateException(
      s"Undefined state [${state.getClass.getName}] in [${entity.getClass.getName}] with id [${entityId}]"
    )
  }

  private def applyEvent(event: E): Unit = {
    val actions = try behavior(state) catch unhandledState
    actions.eventHandler.applyOrElse((event, state), unhandledEvent)
  }

  private def unhandledCommand: PartialFunction[(C, entity.CommandContext, S), entity.Persist[_]] = {
    case cmd =>
      // not using akka.actor.Status.Failure because it is using Java serialization
      sender() ! PersistentEntity.UnhandledCommandException(
        s"Unhandled command [${cmd.getClass.getName}] in [${entity.getClass.getName}] with id [${entityId}]"
      )
      unhandled(cmd)
      entity.persistNone
  }

  def receiveCommand: Receive = {
    case cmd: PersistentEntity.ReplyType[_] =>
      val replyTo = sender()
      val ctx = new entity.CommandContext {
        def reply[R](currentCommand: C with ReplyType[R], msg: R): Unit = {
          if (currentCommand ne cmd) throw new IllegalArgumentException(
            "Reply must be sent in response to the command that is currently processed, " +
              s"Received command is [$cmd], but reply was to [$currentCommand]"
          )
          replyTo.tell(msg, ActorRef.noSender)
        }

        override def commandFailed(cause: Throwable): Unit =
          // not using akka.actor.Status.Failure because it is using Java serialization
          replyTo.tell(cause, ActorRef.noSender)
      }

      try {
        val actions = try behavior(state) catch unhandledState
        val result = actions.commandHandler.applyOrElse((cmd.asInstanceOf[C], ctx, state), unhandledCommand)

        result match {
          case _: entity.PersistNone[_] => // done
          case entity.PersistOne(event, afterPersist) =>
            // apply the event before persist so that validation exception is handled before persisting
            // the invalid event, in case such validation is implemented in the event handler.
            applyEvent(event.asInstanceOf[E])
            persist(tag(event)) { evt =>
              try {
                eventCount += 1
                if (afterPersist != null)
                  afterPersist(event)
                if (snapshotAfter > 0 && eventCount % snapshotAfter == 0)
                  saveSnapshot(state)
              } catch {
                case NonFatal(e) =>
                  ctx.commandFailed(e) // reply with failure
                  throw e
              }
            }
          case entity.PersistAll(events, afterPersist) =>
            // if we trigger snapshot it makes sense to do it after handling all events
            var count = events.size
            var snap = false
            // apply the event before persist so that validation exception is handled before persisting
            // the invalid event, in case such validation is implemented in the event handler.
            events.foreach(e => applyEvent(e.asInstanceOf[E]))
            persistAll(events.map(tag)) { evt =>
              try {
                eventCount += 1
                count -= 1
                if (afterPersist != null && count == 0)
                  afterPersist.apply()
                if (snapshotAfter > 0 && eventCount % snapshotAfter == 0)
                  snap = true
                if (count == 0 && snap)
                  saveSnapshot(state)
              } catch {
                case NonFatal(e) =>
                  ctx.commandFailed(e) // reply with failure
                  throw e
              }
            }
        }
      } catch { // exception thrown from handler.apply
        case NonFatal(e) =>
          ctx.commandFailed(e) // reply with failure
          throw e
      }

    case ReceiveTimeout =>
      context.parent ! ShardRegion.Passivate(PersistentEntityActor.Stop)

    case PersistentEntityActor.Stop =>
      context.stop(self)
  }

  private def tag(event: Any): Any = {
    event match {
      case a: AggregateEvent[_] ⇒
        val tag = a.aggregateTag match {
          case tag: AggregateEventTag[_]       => tag
          case shards: AggregateEventShards[_] => shards.forEntityId(entityId)
        }
        Tagged(event, Set(tag.tag))
      case _ ⇒ event
    }
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    // not using akka.actor.Status.Failure because it is using Java serialization
    sender() ! PersistentEntity.PersistException(
      s"Persist of [${event.getClass.getName}] failed in [${entity.getClass.getName}] with id [${entityId}], " +
        s"caused by: {${cause.getMessage}"
    )
    super.onPersistFailure(cause, event, seqNr)
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    // not using akka.actor.Status.Failure because it is using Java serialization
    sender() ! PersistentEntity.PersistException(
      s"Persist of [${event.getClass.getName}] rejected in [${entity.getClass.getName}] with id [${entityId}], " +
        s"caused by: {${cause.getMessage}"
    )
    super.onPersistFailure(cause, event, seqNr)
  }

}
