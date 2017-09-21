/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import java.net.URLEncoder

import akka.actor.{ ActorSystem, SupervisorStrategy }
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion.{ EntityId, StartEntity }
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.{ ClusterDistribution, ClusterDistributionSettings, ClusterStartupTask }
import com.lightbend.lagom.internal.scaladsl.persistence.ReadSideTagHolderActor.{ CachedTag, GetTag }
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.ExecutionContext

private[lagom] class ReadSideImpl(
  system: ActorSystem, config: ReadSideConfig, registry: PersistentEntityRegistry
)(implicit ec: ExecutionContext, mat: Materializer) extends ReadSide {

  override def register[Event <: AggregateEvent[Event]](processorFactory: => ReadSideProcessor[Event]): Unit =
    registerFactory(() => processorFactory)

  private[lagom] def registerFactory[Event <: AggregateEvent[Event]](
    processorFactory: () => ReadSideProcessor[Event]
  ) = {

    // Only run if we're configured to run on this role
    if (config.role.forall(Cluster(system).selfRoles.contains)) {
      // try to create one instance to fail fast
      val proto = processorFactory()
      val readSideName = proto.readSideName
      val encodedReadSideName = URLEncoder.encode(readSideName, "utf-8")
      val tags = proto.aggregateTags
      val entityIds = tags.map(_.tag)
      val eventClass = tags.headOption match {
        case Some(tag) => tag.eventType
        case None      => throw new IllegalArgumentException(s"ReadSideProcessor ${proto.getClass.getName} returned 0 tags")
      }

      val globalPrepareTask: ClusterStartupTask =
        ClusterStartupTask(
          system,
          s"readSideGlobalPrepare-$encodedReadSideName",
          () => processorFactory().buildHandler().globalPrepare(),
          config.globalPrepareTimeout,
          config.role,
          config.minBackoff,
          config.maxBackoff,
          config.randomBackoffFactor
        )

      val readSideProps =
        ReadSideTagHolderActor.props(
          config,
          globalPrepareTask,
          processorFactory,
          registry,
          eventClass
        )

      val shardingSettings = ClusterShardingSettings(system).withRole(config.role)

      ClusterDistribution(system).start(
        readSideName,
        readSideProps,
        entityIds,
        ClusterDistributionSettings(system).copy(clusterShardingSettings = shardingSettings)
      )
    }

  }
}

import akka.actor.{ Actor, Props }

object ReadSideTagHolderActor {
  def props[Event <: AggregateEvent[Event]](
    config:            ReadSideConfig,
    globalPrepareTask: ClusterStartupTask,
    processorFactory:  () => ReadSideProcessor[Event],
    registry:          PersistentEntityRegistry,
    eventClass:        Class[Event]
  )(implicit mat: Materializer): Props =
    Props(
      new ReadSideTagHolderActor(
        config,
        globalPrepareTask: ClusterStartupTask, processorFactory,
        registry,
        eventClass
      )(
        mat
      )
    )

  // A ReadSideTagHolder is a 1:1 relation to a ReadSideActor. The difference is that
  // the ReadSideActor may die and be respawned by a supervisor while the ReadSideTagHolderActor
  // is a sharded actor that may only be respawned by EnsureActive.
  // because those two respawn operations have different frequencies the TagHolder is provided
  // as a cache for the ReadSideActor to retart faster when it crashes.
  case object GetTag
  case class CachedTag(tag: EntityId)

}

// A ReadSideActor consumes a shard of the whole event stream. That operation
// happens inside a node of cluster. Akka Cluster Sharding is used to distribute
// the tags across the cluster. The distribution of tags happens on a keep-alive
// loop provided in ClusterDistribution.
// So, each node start a ClusterDistribution and a ShardRegion. Then the
// ClusterDistribution runs an infinite loop that constantly sends  all required
// tags to the ShradRegion. The ShardRegion distributes the information to each
// appropriate node where each local ShardRegion redirects each
class ReadSideTagHolderActor[Event <: AggregateEvent[Event]](
  config:            ReadSideConfig,
  globalPrepareTask: ClusterStartupTask,
  processorFactory:  () => ReadSideProcessor[Event],
  registry:          PersistentEntityRegistry,
  eventClass:        Class[Event]
)(implicit mat: Materializer) extends Actor {
  override def receive = {
    case EnsureActive(tag) =>

      val processorProps =
        ReadSideActor.props(
          processorFactory,
          registry.eventStream[Event],
          eventClass,
          globalPrepareTask,
          config.globalPrepareTimeout,
          self
        )

      val backoffProps =
        BackoffSupervisor.propsWithSupervisorStrategy(
          processorProps,
          "processor",
          config.minBackoff,
          config.maxBackoff,
          config.randomBackoffFactor,
          SupervisorStrategy.stoppingStrategy
        )
      context.actorOf(backoffProps)
      context.become(started(tag))
  }

  def started(tag: EntityId): Receive = {
    case GetTag         => sender() ! CachedTag(tag)
    case StartEntity(_) =>
    // already started, nothing to do here
  }

}
