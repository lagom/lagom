/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion
import akka.event.Logging
import akka.japi.Pair
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl
import akka.NotUsed
import com.lightbend.lagom.persistence.AggregateEventTag
import com.lightbend.lagom.persistence.CommandEnvelope
import com.lightbend.lagom.persistence.CorePersistentEntity
import com.lightbend.lagom.persistence.CorePersistentEntityRef
import javax.inject.Inject
import javax.inject.Singleton
import com.lightbend.lagom.persistence.AggregateEvent
import akka.Done
import akka.pattern.ask
import akka.util.Timeout
import com.lightbend.lagom.persistence.CorePersistentEntityRegistry
import scala.concurrent.Future
import scala.reflect._

@Singleton
private[lagom] class InternalPersistentEntityRegistry @Inject() (system: ActorSystem) extends CorePersistentEntityRegistry {

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

  private val eventQueries =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  override def register[C, E, S, Entity <: CorePersistentEntity[C, E, S]: ClassTag](entityFactory: () => Entity): Unit = {

    val entityClass = classTag[Entity].runtimeClass

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

  override def refFor[C](entityClass: Class[_ <: CorePersistentEntity[C, _, _]], entityId: String): CorePersistentEntityRef[C] =
    try
      new CorePersistentEntityRef(entityId, sharding.shardRegion(entityTypeName(entityClass)), system, askTimeout)
    catch {
      case e: IllegalArgumentException =>
        // change the error message
        throw new IllegalArgumentException(s"[${entityClass.getName} must first be registered")
    }

  private def entityTypeName(entityClass: Class[_]): String = Logging.simpleName(entityClass)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Optional[UUID]
  ): scaladsl.Source[Pair[Event, UUID], NotUsed] = {
    val tag = aggregateTag.tag
    val offset = fromOffset.orElse(eventQueries.firstOffset)
    eventQueries.eventsByTag(tag, offset)
      .map { env => Pair.create(env.event.asInstanceOf[Event], env.offset) }
  }

  override def gracefulShutdown(timeout: FiniteDuration): Future[Done] = {
    import scala.collection.JavaConverters._
    val ref = system.actorOf(GracefulLeave.props(registeredTypeNames.keySet.asScala.toSet))
    implicit val t = Timeout(timeout)
    (ref ? GracefulLeave.Leave).mapTo[Done]
  }

}
