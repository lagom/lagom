/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import java.util.Optional
import java.util.concurrent.{ CompletionStage, ConcurrentHashMap, TimeUnit }

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import akka.event.Logging
import akka.japi.Pair
import akka.pattern.ask
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.{ EventsByTagQuery2, EventsByTagQuery }
import akka.stream.javadsl
import akka.util.Timeout
import akka.{ Done, NotUsed }
import com.google.inject.Injector
import com.lightbend.lagom.internal.persistence.cluster.GracefulLeave
import com.lightbend.lagom.javadsl.persistence._

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.util.control.NonFatal

/**
 * Provides shared functionality for implementing a persistent entity registry.
 *
 * Akka persistence plugins can extend this to implement a custom registry.
 */
abstract class AbstractPersistentEntityRegistry(system: ActorSystem, injector: Injector) extends PersistentEntityRegistry {

  /**
   * The ID of the journal.
   */
  protected val journalId: String

  /**
   * The events by tag query. Necessary for implementing read sides and the eventStream query.
   */
  protected val eventsByTagQuery: Option[EventsByTagQuery2] = None

  private val sharding = ClusterSharding(system)
  private val conf = system.settings.config.getConfig("lagom.persistence")
  private val snapshotAfter: Optional[Int] = conf.getString("snapshot-after") match {
    case "off" => Optional.empty()
    case _     => Optional.of(conf.getInt("snapshot-after"))
  }
  private val maxNumberOfShards: Int = conf.getInt("max-number-of-shards")
  private val role: Option[String] = conf.getString("run-entities-on-role") match {
    case "" => None
    case r  => Some(r)
  }
  private val passivateAfterIdleTimeout: FiniteDuration =
    conf.getDuration("passivate-after-idle-timeout", TimeUnit.MILLISECONDS).millis
  private val askTimeout: FiniteDuration = conf.getDuration("ask-timeout", TimeUnit.MILLISECONDS).millis
  private val shardingSettings = ClusterShardingSettings(system).withRole(role)

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case CommandEnvelope(entityId, payload) => (entityId, payload)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case CommandEnvelope(entityId, payload) =>
      (math.abs(entityId.hashCode) % maxNumberOfShards).toString
  }

  private val registeredTypeNames = new ConcurrentHashMap[String, Class[_]]()

  override def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit = {

    val entityFactory: () => PersistentEntity[C, E, S] =
      () => injector.getInstance(entityClass)

    // try to create one instance to fail fast (e.g. wrong constructor)
    val entityTypeName = try {
      entityFactory().entityTypeName
    } catch {
      case NonFatal(e) => throw new IllegalArgumentException("Cannot create instance of " +
        s"[${entityClass.getName}]. The class must extend PersistentEntity and have a " +
        "constructor without parameters.", e)
    }

    // detect non-unique short class names, since that is used as sharding type name
    val alreadyRegistered = registeredTypeNames.putIfAbsent(entityTypeName, entityClass)
    if (alreadyRegistered != null && !alreadyRegistered.equals(entityClass)) {
      throw new IllegalArgumentException(s"The entityTypeName [$entityTypeName] for entity " +
        s"[${entityClass.getName}] is not unique. It is already registered by [${alreadyRegistered.getName}]. " +
        "Override entityTypeName in the PersistentEntity to define a unique name.")
    }

    if (role.forall(Cluster(system).selfRoles.contains)) {
      val entityProps = PersistentEntityActor.props(
        persistenceIdPrefix = entityTypeName, Optional.empty(), entityFactory, snapshotAfter, passivateAfterIdleTimeout
      )
      sharding.start(entityTypeName, entityProps, shardingSettings, extractEntityId, extractShardId)
    } else {
      // not required role, start in proxy mode
      sharding.startProxy(entityTypeName, role, extractEntityId, extractShardId)
    }
  }

  override def refFor[C](entityClass: Class[_ <: PersistentEntity[C, _, _]], entityId: String): PersistentEntityRef[C] =
    try
      new PersistentEntityRef(entityId, sharding.shardRegion(entityTypeName(entityClass)), system, askTimeout)
    catch {
      case e: IllegalArgumentException =>
        // change the error message
        throw new IllegalArgumentException(s"[${entityClass.getName} must first be registered")
    }

  private def entityTypeName(entityClass: Class[_]): String = Logging.simpleName(entityClass)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): javadsl.Source[Pair[Event, Offset], NotUsed] = {
    eventsByTagQuery match {
      case Some(queries) =>
        val tag = aggregateTag.tag

        val offset = OffsetAdapter.dslOffsetToOffset(fromOffset)

        queries.eventsByTag(tag, offset)
          .map { env => Pair.create(env.event.asInstanceOf[Event], OffsetAdapter.offsetToDslOffset(env.offset)) }
          .asJava

      case None =>
        throw new UnsupportedOperationException(s"The $journalId Lagom persistence plugin does not support streaming events by tag")
    }
  }

  override def gracefulShutdown(timeout: FiniteDuration): CompletionStage[Done] = {
    import scala.collection.JavaConverters._
    import scala.compat.java8.FutureConverters._
    val ref = system.actorOf(GracefulLeave.props(registeredTypeNames.keySet.asScala.toSet))
    implicit val t = Timeout(timeout)
    (ref ? GracefulLeave.Leave).mapTo[Done].toJava
  }

}
