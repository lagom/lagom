/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import java.net.URLDecoder
import java.util.Optional

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
import akka.actor.actorRef2Scala
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventShards, AggregateEventTag, PersistentEntity }
import java.util.function.{ BiFunction => JBiFunction }
import java.util.function.{ Function => JFunction }

import akka.persistence.journal.Tagged
import play.api.Logger

private[lagom] object PersistentEntityActor {
  def props[C, E, S](
    persistenceIdPrefix:       String,
    entityId:                  Optional[String],
    entityFactory:             () => PersistentEntity[C, E, S],
    snapshotAfter:             Optional[Int],
    passivateAfterIdleTimeout: FiniteDuration
  ): Props =
    Props(new PersistentEntityActor(persistenceIdPrefix, entityId, entityFactory(), snapshotAfter.orElse(0),
      passivateAfterIdleTimeout))

  /**
   * Stop the actor for passivation. `PoisonPill` does not work well
   * with persistent actors.
   */
  case object Stop
}

/**
 * The `PersistentActor` that runs a [[com.lightbend.lagom.javadsl.persistence.PersistentEntity]].
 */
private[lagom] class PersistentEntityActor[C, E, S](
  persistenceIdPrefix:       String,
  id:                        Optional[String],
  entity:                    PersistentEntity[C, E, S],
  snapshotAfter:             Int,
  passivateAfterIdleTimeout: FiniteDuration
) extends PersistentActor {
  private val log = Logger(this.getClass)

  private val entityId: String = id.orElse(
    URLDecoder.decode(self.path.name, ByteString.UTF_8)
  )

  override val persistenceId: String = persistenceIdPrefix + entityId

  entity.internalSetEntityId(entityId)

  private var eventCount = 0L

  context.setReceiveTimeout(passivateAfterIdleTimeout)

  // create a new instance every time, to capture sender()
  private def newCtx(): entity.CommandContext[Any] = new entity.CommandContext[Any] {
    private val replyTo: ActorRef = sender()

    override def reply(msg: Any): Unit =
      replyTo ! msg

    override def commandFailed(cause: Throwable): Unit =
      // not using akka.actor.Status.Failure because it is using Java serialization
      reply(cause)

  }

  private def eventHandlers: Map[Class[E], JFunction[E, entity.Behavior]] =
    entity.behavior.eventHandlers.asInstanceOf[Map[Class[E], JFunction[E, entity.Behavior]]]

  private def commandHandlers: Map[Class[C], JBiFunction[C, entity.CommandContext[Any], entity.Persist[E]]] =
    entity.behavior.commandHandlers.asInstanceOf[Map[Class[C], JBiFunction[C, entity.CommandContext[Any], entity.Persist[E]]]]

  override def receiveRecover: Receive = {

    var initialized = false

    def initEmpty(): Unit =
      if (!initialized) {
        val inital = entity.initialBehavior(Optional.empty[S])
        entity.internalSetCurrentBehavior(inital)
        initialized = true
      }

    {
      case SnapshotOffer(_, snapshot) =>
        if (!initialized) {
          val inital = entity.initialBehavior(Optional.ofNullable(snapshot.asInstanceOf[S]))
          entity.internalSetCurrentBehavior(inital)
          initialized = true
        }

      case RecoveryCompleted =>
        initEmpty()
        val newBehavior = entity.recoveryCompleted()
        entity.internalSetCurrentBehavior(newBehavior)

      case evt =>
        initEmpty()
        applyEvent(evt)
        eventCount += 1

    }
  }

  private def applyEvent(event: Any): Unit = {
    eventHandlers.get(event.getClass.asInstanceOf[Class[E]]) match {
      case Some(handler) =>
        val newBehavior = handler.apply(event.asInstanceOf[E])
        entity.internalSetCurrentBehavior(newBehavior)
      case None =>
        log.warn(s"Unhandled event [${event.getClass.getName}] in [${entity.getClass.getName}] with id [${entityId}]")
    }
  }

  def receiveCommand: Receive = {
    case cmd: PersistentEntity.ReplyType[_] =>
      commandHandlers.get(cmd.getClass.asInstanceOf[Class[C]]) match {
        case Some(handler) =>
          val ctx = newCtx()
          try handler.apply(cmd.asInstanceOf[C], ctx) match {
            case _: entity.PersistNone[_] => // done
            case entity.PersistOne(event, afterPersist) =>
              // apply the event before persist so that validation exception is handled before persisting
              // the invalid event, in case such validation is implemented in the event handler.
              applyEvent(event)
              persist(tag(event)) { evt =>
                try {
                  eventCount += 1
                  if (afterPersist != null)
                    afterPersist.accept(event)
                  if (snapshotAfter > 0 && eventCount % snapshotAfter == 0)
                    saveSnapshot(entity.behavior.state)
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
              events.foreach(applyEvent)
              persistAll(events.map(tag)) { evt =>
                try {
                  eventCount += 1
                  count -= 1
                  if (afterPersist != null && count == 0)
                    afterPersist.apply()
                  if (snapshotAfter > 0 && eventCount % snapshotAfter == 0)
                    snap = true
                  if (count == 0 && snap)
                    saveSnapshot(entity.behavior.state)
                } catch {
                  case NonFatal(e) =>
                    ctx.commandFailed(e) // reply with failure
                    throw e
                }
              }
          } catch { // exception thrown from handler.apply
            case NonFatal(e) =>
              ctx.commandFailed(e) // reply with failure
              throw e
          }

        case None =>
          // not using akka.actor.Status.Failure because it is using Java serialization
          sender() ! PersistentEntity.UnhandledCommandException(
            s"Unhandled command [${cmd.getClass.getName}] in [${entity.getClass.getName}] with id [${entityId}]"
          )
          unhandled(cmd)
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
