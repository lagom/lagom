/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.persistence

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion
import akka.event.Logging
import akka.pattern.ask
import akka.persistence.query.Offset
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.stream.scaladsl
import akka.util.Timeout
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.scaladsl.persistence._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
 * Provides shared functionality for implementing a persistent entity registry.
 *
 * Akka persistence plugins can extend this to implement a custom registry.
 */
class AbstractPersistentEntityRegistry(system: ActorSystem) extends PersistentEntityRegistry {

  protected val name: Option[String]          = None
  protected val journalPluginId: String       = ""
  protected val snapshotPluginId: String      = ""
  protected val queryPluginId: Option[String] = None

  private lazy val eventsByTagQuery: Option[EventsByTagQuery] =
    queryPluginId.map(id => PersistenceQuery(system).readJournalFor[EventsByTagQuery](id))

  private val sharding = ClusterSharding(system)
  private val conf     = system.settings.config.getConfig("lagom.persistence")
  private val snapshotAfter: Option[Int] = conf.getString("snapshot-after") match {
    case "off" => None
    case _     => Some(conf.getInt("snapshot-after"))
  }
  private val maxNumberOfShards: Int = conf.getInt("max-number-of-shards")
  private val role: Option[String] = conf.getString("run-entities-on-role") match {
    case "" => None
    case r  => Some(r)
  }
  private val passivateAfterIdleTimeout: Duration = {
    val durationMs = conf.getDuration("passivate-after-idle-timeout", TimeUnit.MILLISECONDS)
    if (durationMs == 0) {
      // Scaladoc of setReceiveTimeout says "Pass in `Duration.Undefined` to switch off this feature."
      Duration.Undefined
    } else {
      durationMs.millis
    }
  }

  private val askTimeout: FiniteDuration = conf.getDuration("ask-timeout", TimeUnit.MILLISECONDS).millis
  private val shardingSettings           = ClusterShardingSettings(system).withRole(role)

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case CommandEnvelope(entityId, payload) => (entityId, payload)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case CommandEnvelope(entityId, payload) =>
      (math.abs(entityId.hashCode) % maxNumberOfShards).toString
  }

  private val registeredTypeNames = new ConcurrentHashMap[String, Class[_]]()
  private val reverseRegister     = new ConcurrentHashMap[Class[_], String]()

  private def prependName(entityTypeName: String) = name.fold("")(_ + "-") + entityTypeName

  override def register(entityFactory: => PersistentEntity): Unit = {

    // try to create one instance to fail fast
    val proto          = entityFactory
    val entityTypeName = proto.entityTypeName
    val entityClass    = proto.getClass

    // detect non-unique short class names, since that is used as sharding type name
    val alreadyRegistered = registeredTypeNames.putIfAbsent(entityTypeName, entityClass)
    if (alreadyRegistered != null && !alreadyRegistered.equals(entityClass)) {
      throw new IllegalArgumentException(
        s"The entityTypeName [$entityTypeName] for entity " +
          s"[${entityClass.getName}] is not unique. It is already registered by [${alreadyRegistered.getName}]. " +
          "Override entityTypeName in the PersistentEntity to define a unique name."
      )
    }
    // if the entityName is deemed unique, we add the entity to the reverse index:
    reverseRegister.putIfAbsent(entityClass, entityTypeName)

    if (role.forall(Cluster(system).selfRoles.contains)) {
      val entityProps = PersistentEntityActor.props(
        persistenceIdPrefix = entityTypeName,
        None,
        () => entityFactory,
        snapshotAfter,
        passivateAfterIdleTimeout,
        journalPluginId,
        snapshotPluginId
      )
      sharding.start(prependName(entityTypeName), entityProps, shardingSettings, extractEntityId, extractShardId)
    } else {
      // not required role, start in proxy mode
      sharding.startProxy(prependName(entityTypeName), role, extractEntityId, extractShardId)
    }
  }

  override def refFor[P <: PersistentEntity: ClassTag](entityId: String): PersistentEntityRef[P#Command] = {
    val entityClass = implicitly[ClassTag[P]].runtimeClass.asInstanceOf[Class[P]]
    val entityName  = reverseRegister.get(entityClass)
    if (entityName == null) throw new IllegalArgumentException(s"[${entityClass.getName} must first be registered")
    new PersistentEntityRef(entityId, sharding.shardRegion(prependName(entityName)), system, askTimeout)
  }

  private def entityTypeName(entityClass: Class[_]): String = Logging.simpleName(entityClass)

  override def eventStream[Event <: AggregateEvent[Event]](
      aggregateTag: AggregateEventTag[Event],
      fromOffset: Offset
  ): scaladsl.Source[EventStreamElement[Event], NotUsed] = {
    eventsByTagQuery match {
      case Some(queries) =>
        val tag = aggregateTag.tag

        queries
          .eventsByTag(tag, fromOffset)
          .map(
            env =>
              new EventStreamElement[Event](
                PersistentEntityActor.extractEntityId(env.persistenceId),
                env.event.asInstanceOf[Event],
                env.offset
              )
          )
      case None =>
        throw new UnsupportedOperationException(
          s"The Lagom persistence plugin does not support streaming events by tag"
        )
    }
  }

}
