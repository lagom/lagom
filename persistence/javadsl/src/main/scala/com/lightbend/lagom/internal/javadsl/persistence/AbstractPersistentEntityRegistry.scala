/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import java.util.Optional
import java.util.concurrent.{ CompletionStage, ConcurrentHashMap, TimeUnit }

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.cluster.Cluster
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import akka.event.Logging
import akka.japi.Pair
import akka.pattern.ask
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.javadsl
import akka.util.Timeout
import akka.{ Done, NotUsed }
import com.google.inject.Injector
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
  protected val eventsByTagQuery: Option[EventsByTagQuery] = None

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
  private val shardingSettings = ClusterShardingSettings(system).withRole(role)

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case CommandEnvelope(entityId, payload) => (entityId, payload)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case CommandEnvelope(entityId, payload) =>
      (math.abs(entityId.hashCode) % maxNumberOfShards).toString
  }

  private val registeredTypeNames = new ConcurrentHashMap[String, Class[_]]()
  private val reverseRegister = new ConcurrentHashMap[Class[_], String]()

  override def register[C, E, S](entityClass: Class[_ <: PersistentEntity[C, E, S]]): Unit = {

    val entityFactory: () => PersistentEntity[C, E, S] =
      () => injector.getInstance(entityClass)

    // try to create one instance to fail fast (e.g. wrong constructor)
    val entityTypeName = try {
      entityFactory().entityTypeName
    } catch {
      case NonFatal(e) => throw new IllegalArgumentException("Cannot create instance of " +
        s"[${entityClass.getName}]. The class must extend PersistentEntity and have a " +
        "constructor without parameters or annotated with @Inject.", e)
    }

    // detect non-unique short class names, since that is used as sharding type name
    val alreadyRegistered = registeredTypeNames.putIfAbsent(entityTypeName, entityClass)
    if (alreadyRegistered != null && !alreadyRegistered.equals(entityClass)) {
      throw new IllegalArgumentException(s"The entityTypeName [$entityTypeName] for entity " +
        s"[${entityClass.getName}] is not unique. It is already registered by [${alreadyRegistered.getName}]. " +
        "Override entityTypeName in the PersistentEntity to define a unique name.")
    }
    // if the entityName is deemed unique, we add the entity to the reverse index:
    reverseRegister.putIfAbsent(entityClass, entityTypeName)

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

  override def refFor[C](entityClass: Class[_ <: PersistentEntity[C, _, _]], entityId: String): PersistentEntityRef[C] = {
    val entityName = reverseRegister.get(entityClass)
    if (entityName == null) throw new IllegalArgumentException(s"[${entityClass.getName} must first be registered")
    new PersistentEntityRef(entityId, sharding.shardRegion(entityName), askTimeout)
  }

  private def entityTypeName(entityClass: Class[_]): String = Logging.simpleName(entityClass)

  override def eventStream[Event <: AggregateEvent[Event]](
    aggregateTag: AggregateEventTag[Event],
    fromOffset:   Offset
  ): javadsl.Source[Pair[Event, Offset], NotUsed] = {
    eventsByTagQuery match {
      case Some(queries) =>
        val tag = aggregateTag.tag

        val startingOffset = mapStartingOffset(fromOffset)

        queries.eventsByTag(tag, startingOffset)
          .map { env => Pair.create(env.event.asInstanceOf[Event], OffsetAdapter.offsetToDslOffset(env.offset)) }
          .asJava

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
  protected def mapStartingOffset(storedOffset: Offset): AkkaOffset = OffsetAdapter.dslOffsetToOffset(storedOffset)

  override def gracefulShutdown(timeout: FiniteDuration): CompletionStage[Done] = {
    import scala.collection.JavaConverters._
    import scala.compat.java8.FutureConverters._

    //
    // TODO: When applying CoordinatedShutdown globally in Lagom and Play this should probably change and the
    // method 'gracefulShutdown' removed from Lagom's API.
    //
    // More info at: https://doc.akka.io/docs/akka/2.5/scala/actors.html#coordinated-shutdown
    //
    // NOTE: the default is for CoordinatedShutdown to _not_ terminate the JVM (which is what we want in
    // Lagom at the moment).
    //
    // NOTE: Because this uses CoordinatedShutdown, this method no longer stops PE ShardRegions alone, it now
    // stops all ShardRegions (aka, Kafka subscribers, read-side processors, ...). The reason is Akka 2.5 registers
    // all and every 'ShardRegion' instance into the ClusterShardingShutdownRegion phase of CoordinatedShutdown:
    // https://github.com/akka/akka/blob/ad9de435a2b434b065ae0956e059a45960b9e9d3/akka-cluster-sharding/src/main/scala/akka/cluster/sharding/ShardRegion.scala#L407-L412
    //
    // WARNING: Using CoordinatedShutdown could interfere with JoinClusterImpl
    // https://github.com/lagom/lagom/blob/e53bda5fd2f83aaa4cd639c9ad27d6053ede812f/cluster/core/src/main/scala/com/lightbend/lagom/internal/cluster/JoinClusterImpl.scala#L37-L58
    //
    // Uses Akka 2.5's CoordinatedShutdown but instead of invoking the complete sequence of stages
    // invokes the shutdown starting at "before-cluster-shutdown".
    //
    CoordinatedShutdown(system).run(Some("before-cluster-shutdown")).toJava
  }

}
