/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.actor.{ ActorContext, ActorRef, PoisonPill }
import akka.util.Timeout
import com.lightbend.lagom.internal.scaladsl.persistence.PersistentEntityActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
 * A persistent entity that runs as a child actor of the current actor context.
 *
 * @param actor The actor for direct access if necessary.
 */
sealed class ChildPersistentEntity[Command](val actor: ActorRef) {
  /**
   * Send a message to the actor.
   */
  def !(msg: Command)(implicit sender: ActorRef): Unit = actor ! msg

  /**
   * Ask the actor with a command.
   */
  def ?[Cmd <: Command with PersistentEntity.ReplyType[_]](command: Cmd)(implicit timeout: Timeout): Future[command.ReplyType] = {
    import akka.pattern.ask

    (actor ? command).asInstanceOf[Future[command.ReplyType]]
  }

  /**
   * Forward the command to the entity.
   */
  def forward(msg: Command)(implicit ctx: ActorContext): Unit = actor forward msg

  /**
   * Stop the persistent entity.
   */
  def stop(): Unit = {
    actor ! PersistentEntityActor.Stop
  }
}

object ChildPersistentEntity {

  /**
   * Instantiate a persistent entity as a child of the current actor context.
   *
   * Note that since this is a direct child, this provides no guarantee that this is the only entity with the given id
   * running in the cluster, or even in the actor system. It is up to the actor that instantiates this to ensure that
   * only one instance of the entity across the cluster is instantiated (by using cluster sharding to distribute
   * itself, for example), or to deal with inconsistencies that may arise due to having multiple entities instantiated
   * in the cluster.
   *
   * @param factory   The entity factory.
   * @param entityId  The entity ID.
   * @param actorName The actor name.
   */
  def apply[P <: PersistentEntity: ClassTag](factory: () => P, entityId: String, actorName: String)(implicit ctx: ActorContext): ChildPersistentEntity[P#Command] = {

    ctx.child(actorName) match {

      case Some(actorRef) => new ChildPersistentEntity(actorRef)
      case None =>

        val conf = ctx.system.settings.config.getConfig("lagom.persistence")
        val snapshotAfter: Option[Int] = conf.getString("snapshot-after") match {
          case "off" => None
          case _     => Some(conf.getInt("snapshot-after"))
        }

        val entity = factory()

        val actorRef = ctx.actorOf(PersistentEntityActor.props(
          persistenceIdPrefix = entity.entityTypeName,
          entityId = Some(entityId),
          entityFactory = () => entity,
          snapshotAfter = snapshotAfter,
          passivateAfterIdleTimeout = Duration.Undefined
        ), actorName)

        new ChildPersistentEntity(actorRef)
    }
  }
}

/**
 * Factory for creating child persistent entities.
 *
 * This can be used instead of directly invoking [[ChildPersistentEntity.apply()]] as a method of indirection to
 * allow you to inject a mocked actor (eg an Akka TestKit probe) when testing.
 */
trait ChildPersistentEntityFactory[P <: PersistentEntity] {

  /**
   * Create an entity.
   *
   * @param entityId  The id of the entity.
   * @param actorName The name of the actor.
   */
  def apply(entityId: String, actorName: String)(implicit ctx: ActorContext): ChildPersistentEntity[P#Command]
}

object ChildPersistentEntityFactory {

  /**
   * Create a [[ChildPersistentEntityFactory]] for the given entity.
   *
   * @param factory The factory to create the entity.
   */
  def forEntity[P <: PersistentEntity: ClassTag](factory: () => P): ChildPersistentEntityFactory[P] = {
    new ChildPersistentEntityFactory[P] {
      override def apply(entityId: String, actorName: String)(implicit ctx: ActorContext): ChildPersistentEntity[P#Command] = {
        ChildPersistentEntity(factory, entityId, actorName)
      }
    }
  }

  /**
   * Create a mocked [[ChildPersistentEntityFactory]].
   *
   * All commands sent to any entities created will be passed as is to the `testProbe` actor. When `stop` is invoked,
   * the actor will be sent a `PoisonPill`.
   *
   * This is intended to be used with an Akka TestKit TestProbe, for example:
   *
   * {{{
   * val entityProbe = TestProbe("persistent-entity")
   * val childEntityFactory = ChildPersistentEntityFactory.mocked[MyEntity](entityProbe.ref)
   *
   * // Create actor and cause it to send a command to the entity
   * ...
   *
   * entityProbe.expectMsg(5.seconds, MyCommand)
   * entityProbe.reply(SomeReply)
   * }}}
   *
   * @param testProbe The test probe ActorRef.
   */
  def mocked[P <: PersistentEntity](testProbe: ActorRef): ChildPersistentEntityFactory[P] = {
    new ChildPersistentEntityFactory[P] {
      override def apply(entityId: String, actorName: String)(implicit ctx: ActorContext): ChildPersistentEntity[P#Command] = {
        new ChildPersistentEntity[P#Command](testProbe) {
          override def stop(): Unit = {
            actor ! PoisonPill
          }
        }
      }
    }
  }
}
