/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import java.util.concurrent.{ ConcurrentHashMap, TimeUnit }

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import akka.event.Logging
import akka.pattern.ask
import akka.persistence.query.Offset
import akka.persistence.query.scaladsl.EventsByTagQuery2
import akka.stream.scaladsl
import akka.util.Timeout
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.persistence.cluster.GracefulLeave
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
 * Provides shared functionality for implementing a persistent entity registry.
 *
 * Akka persistence plugins can extend this to implement a custom registry.
 */
abstract class AbstractPersistentEntityRegistry(system: ActorSystem) extends PersistentEntityRegistry {

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
  private val snapshotAfter: Option[Int] = conf.getString("snapshot-after") match {
    case "off" => None
    case _     => Some(conf.getInt("snapshot-after"))
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

  override def register(entityFactory: => PersistentEntity): Unit = {

    // try to create one instance to fail fast
    val proto = entityFactory
    val entityTypeName = proto.entityTypeName
    val entityClass = proto.getClass

    // detect non-unique short class names, since that is used as sharding type name
    val alreadyRegistered = registeredTypeNames.putIfAbsent(entityTypeName, entityClass)
    if (alreadyRegistered != null && !alreadyRegistered.equals(entityClass)) {
      throw new IllegalArgumentException(s"The entityTypeName [$entityTypeName] for entity " +
        s"[${entityClass.getName}] is not unique. It is already registered by [${alreadyRegistered.getName}]. " +
        "Override entityTypeName in the PersistentEntity to define a unique name.")
    }

    if (role.forall(Cluster(system).selfRoles.contains)) {
      val entityProps = PersistentEntityActor.props(
        persistenceIdPrefix = entityTypeName, None, () => entityFactory, snapshotAfter, passivateAfterIdleTimeout
      )
      sharding.start(entityTypeName, entityProps, shardingSettings, extractEntityId, extractShardId)
    } else {
      // not required role, start in proxy mode
      sharding.startProxy(entityTypeName, role, extractEntityId, extractShardId)
    }
  }

  override def refFor[P <: PersistentEntity: ClassTag](entityId: String): PersistentEntityRef[P#Command] = {
    val entityClass = implicitly[ClassTag[P]].runtimeClass.asInstanceOf[Class[P]]
    try
      new PersistentEntityRef(entityId, sharding.shardRegion(entityTypeName(entityClass)), system, askTimeout)
    catch {
      case e: IllegalArgumentException =>
        // change the error message
        throw new IllegalArgumentException(s"[${entityClass.getName} must first be registered")
    }
  }

  private def entityTypeName(entityClass: Class[_]): String = Logging.simpleName(entityClass)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): scaladsl.Source[EventStreamElement[Event], NotUsed] = {
    eventsByTagQuery match {
      case Some(queries) =>
        val tag = aggregateTag.tag

        val startingOffset = mapStartingOffset(fromOffset)

        queries.eventsByTag(tag, startingOffset)
          .map(env =>
            new EventStreamElement[Event](
              PersistentEntityActor.extractEntityId(env.persistenceId),
              env.event.asInstanceOf[Event],
              env.offset
            ))
      case None =>
        throw new UnsupportedOperationException(s"The $journalId Lagom persistence plugin does not support streaming events by tag")
    }
  }

  /**
   * Converts a stored event journal offset into the argument to an
   * `eventsByTag` query.
   *
   * Different Akka Persistence back ends interpret the `offset` parameter to
   * `eventsByTag` differently. Some return events starting ''after'' the given
   * `offset`, others start with the event ''at'' that offset. In Lagom, we
   * shield end users from this difference, and always want to return
   * unprocessed events. Subclasses can override this method as necessary to
   * convert a stored offset into a starting offset that ensures no stored
   * values will be repeated in the stream.
   *
   * @param storedOffset the most recently seen offset
   * @return an offset that can be provided to the `eventsByTag` query to
   *         retrieve only unseen events
   */
  protected def mapStartingOffset(storedOffset: Offset): Offset = storedOffset

  override def gracefulShutdown(timeout: FiniteDuration): Future[Done] = {
    import scala.collection.JavaConverters._
    val ref = system.actorOf(GracefulLeave.props(registeredTypeNames.keySet.asScala.toSet))
    implicit val t = Timeout(timeout)
    (ref ? GracefulLeave.Leave).mapTo[Done]
  }

}
