/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import java.net.URLDecoder

import akka.actor.{ Props, ReceiveTimeout }
import akka.cluster.sharding.ShardRegion
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.persistence.journal.Tagged
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.persistence.{ AggregateEvent, AggregateEventShards, AggregateEventTag, PersistentEntity }
import play.api.Logger

import scala.concurrent.duration.FiniteDuration
import scala.util.control.Exception.Catcher
import scala.util.control.NonFatal

private[lagom] object PersistentEntityActor {
  def props(
    persistenceIdPrefix:       String,
    entityId:                  Option[String],
    entityFactory:             () => PersistentEntity,
    snapshotAfter:             Option[Int],
    passivateAfterIdleTimeout: FiniteDuration
  ): Props =
    Props(new PersistentEntityActor(persistenceIdPrefix, entityId,
      entityFactory(),
      snapshotAfter.getOrElse(0), passivateAfterIdleTimeout))

  /**
   * Stop the actor for passivation. `PoisonPill` does not work well
   * with persistent actors.
   */
  case object Stop

  val EntityIdSeparator = '|'

  /**
   * @return the entity id part encoded in the persistence id
   */
  def extractEntityId(persistenceId: String): String = {
    val idx = persistenceId.indexOf(EntityIdSeparator)
    if (idx > 0) {
      persistenceId.substring(idx + 1)
    } else throw new IllegalArgumentException(
      s"Cannot split '$persistenceId' into persistenceIdPrefix and entityId " +
        s"because there is no separator character ('$EntityIdSeparator')"
    )
  }

}

/**
 * The `PersistentActor` that runs a [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]].
 */
private[lagom] class PersistentEntityActor(
  persistenceIdPrefix:       String,
  id:                        Option[String],
  entity:                    PersistentEntity,
  snapshotAfter:             Int,
  passivateAfterIdleTimeout: FiniteDuration
) extends PersistentActor {

  import PersistentEntityActor.EntityIdSeparator

  // we don't care about the types in the actor, but using these aliases for better readability
  private type C = entity.Command
  private type E = entity.Event
  private type S = entity.State

  private val log = Logger(this.getClass)

  private val entityId: String = id.getOrElse(
    URLDecoder.decode(self.path.name, ByteString.UTF_8)
  )
  require(
    !persistenceIdPrefix.contains(EntityIdSeparator),
    s"persistenceIdPrefix '$persistenceIdPrefix' contains '$EntityIdSeparator' which is a reserved character"
  )

  override val persistenceId: String = persistenceIdPrefix + EntityIdSeparator + entityId

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
    case (event, _) =>
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
    state = actions.eventHandler.applyOrElse((event, state), unhandledEvent)
  }

  private def unhandledCommand: PartialFunction[(C, entity.CommandContext[Any], S), entity.Persist[_]] = {
    case (cmd, _, _) =>
      // not using akka.actor.Status.Failure because it is using Java serialization
      sender() ! PersistentEntity.UnhandledCommandException(
        s"Unhandled command [${cmd.getClass.getName}] in [${entity.getClass.getName}] with id [${entityId}]"
      )
      unhandled(cmd)
      entity.persistNone
  }

  def receiveCommand: Receive = {
    case cmd: PersistentEntity.ReplyType[Any] @unchecked =>
      val replyTo = sender()
      val ctx = new entity.CommandContext[Any] {
        override def reply(msg: Any): Unit = replyTo ! msg

        override def commandFailed(cause: Throwable): Unit =
          // not using akka.actor.Status.Failure because it is using Java serialization
          replyTo ! cause
      }

      try {
        val actions = try behavior(state) catch unhandledState
        val commandHandler = actions.commandHandlers.get(cmd.getClass) match {
          case Some(h) => h
          case None    => PartialFunction.empty
        }
        val result = commandHandler.applyOrElse((cmd.asInstanceOf[C], ctx, state), unhandledCommand)
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
    import scala.language.existentials
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
