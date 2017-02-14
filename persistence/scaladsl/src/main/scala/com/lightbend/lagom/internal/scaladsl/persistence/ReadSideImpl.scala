/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import java.net.URLEncoder

import akka.actor.{ ActorSystem, SupervisorStrategy }
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterShardingSettings
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.{ ClusterDistribution, ClusterDistributionSettings, ClusterStartupTask }
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

      val globalPrepareTask = ClusterStartupTask(
        system, s"readSideGlobalPrepare-$encodedReadSideName",
        () => processorFactory().buildHandler.globalPrepare(),
        config.globalPrepareTimeout, config.role, config.minBackoff, config.maxBackoff, config.randomBackoffFactor
      )

      val processorProps = ReadSideActor.props(processorFactory, registry.eventStream[Event], eventClass, globalPrepareTask, config.globalPrepareTimeout)

      val backoffProps = BackoffSupervisor.propsWithSupervisorStrategy(
        processorProps, "processor", config.minBackoff, config.maxBackoff, config.randomBackoffFactor, SupervisorStrategy.stoppingStrategy
      )

      val shardingSettings = ClusterShardingSettings(system).withRole(config.role)

      ClusterDistribution(system).start(
        readSideName,
        backoffProps,
        entityIds.toSet,
        ClusterDistributionSettings(system).copy(clusterShardingSettings = shardingSettings)
      )
    }

  }
}
