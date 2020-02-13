/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion
import akka.japi
import akka.japi.function
import akka.persistence.query.EventEnvelope
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.javadsl
import akka.stream.scaladsl
import com.lightbend.lagom.javadsl.persistence._
import play.api.inject.Injector

import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * Provides shared functionality for implementing a persistent entity registry.
 *
 * Akka persistence plugins can extend this to implement a custom registry.
 */
class AbstractPersistentEntityRegistry(
    system: ActorSystem,
    injector: Injector,
) extends PersistentEntityRegistry {
  protected val name: Optional[String]          = Optional.empty()
  protected val journalPluginId: String         = ""
  protected val snapshotPluginId: String        = ""
  protected val queryPluginId: Optional[String] = Optional.empty()

  private lazy val eventsByTagQuery: Option[EventsByTagQuery] =
    queryPluginId.asScala.map(id => PersistenceQuery(system).readJournalFor[EventsByTagQuery](id))

  private val sharding = ClusterSharding(system)
  private val conf     = system.settings.config.getConfig("lagom.persistence")
  private val snapshotAfter: Optional[Int] = conf.getString("snapshot-after") match {
    case "off" => Optional.empty()
    case _     => Optional.of(conf.getInt("snapshot-after"))
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
    case CommandEnvelope(entityId, _) =>
      (math.abs(entityId.hashCode) % maxNumberOfShards).toString
  }

  private val registeredTypeNames = new ConcurrentHashMap[String, Class[_]]()
  private val reverseRegister     = new ConcurrentHashMap[Class[_], String]()

  private def prependName(entityTypeName: String) = name.asScala.fold("")(_ + "-") + entityTypeName

  override def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit = {
    val entityFactory: () => PersistentEntity[C, E, S] =
      () => injector.instanceOf(entityClass)

    // try to create one instance to fail fast (e.g. wrong constructor)
    val entityTypeName =
      try {
        entityFactory().entityTypeName
      } catch {
        case NonFatal(e) =>
          throw new IllegalArgumentException(
            "Cannot create instance of " +
              s"[${entityClass.getName}]. The class must extend PersistentEntity and have a " +
              "constructor without parameters or annotated with @Inject.",
            e
          )
      }

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
        Optional.empty(),
        entityFactory,
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

  override def refFor[C](
      entityClass: Class[_ <: PersistentEntity[C, _, _]],
      entityId: String
  ): PersistentEntityRef[C] = {
    val entityName = reverseRegister.get(entityClass)
    if (entityName == null) throw new IllegalArgumentException(s"[${entityClass.getName} must first be registered")
    new PersistentEntityRef(entityId, sharding.shardRegion(prependName(entityName)), askTimeout)
  }

  override def eventStream[Event <: AggregateEvent[Event]](
      aggregateTag: AggregateEventTag[Event],
      fromOffset: Offset
  ): javadsl.Source[japi.Pair[Event, Offset], NotUsed] = {
    eventEnvelopeStream(aggregateTag, fromOffset)
      .map(AbstractPersistentEntityRegistry.toStreamElement)

  }
  @InternalApi
  private[lagom] override def eventEnvelopeStream[Event <: AggregateEvent[Event]](
      aggregateTag: AggregateEventTag[Event],
      fromOffset: Offset
  ): javadsl.Source[EventEnvelope, NotUsed] = {
    eventsByTagQuery match {
      case Some(queries) =>
        val startingOffset = mapStartingOffset(fromOffset)

        queries
          .eventsByTag(aggregateTag.tag, startingOffset)
          .asJava

      case None =>
        throw new UnsupportedOperationException(
          s"The Lagom persistence plugin does not support streaming events by tag"
        )
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
  protected def mapStartingOffset(storedOffset: Offset): AkkaOffset = OffsetAdapter.dslOffsetToOffset(storedOffset)
}

private[lagom] object AbstractPersistentEntityRegistry {

  @InternalApi
  def toStreamElement[Event <: AggregateEvent[Event]](env: EventEnvelope): japi.Pair[Event, Offset] =
    japi.Pair.create(env.event.asInstanceOf[Event], OffsetAdapter.offsetToDslOffset(env.offset))

}
