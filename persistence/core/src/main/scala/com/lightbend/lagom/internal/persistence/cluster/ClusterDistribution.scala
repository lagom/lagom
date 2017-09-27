/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cluster

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorRef, ActorSystem, DeadLetterSuppression, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props, Terminated }
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion.EntityId
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import com.typesafe.config.Config

import scala.concurrent.duration._

/**
 * Settings for cluster distribution.
 *
 * @param clusterShardingSettings The cluster sharding settings.
 * @param ensureActiveInterval The interval at which entities are ensured to be active.
 */
case class ClusterDistributionSettings(
  clusterShardingSettings: ClusterShardingSettings,
  ensureActiveInterval:    FiniteDuration
)

object ClusterDistributionSettings {
  def apply(system: ActorSystem): ClusterDistributionSettings = {
    val clusterShardingSettings = ClusterShardingSettings(system)
    val config = system.settings.config.getConfig("lagom.persistence.cluster.distribution")
    ClusterDistributionSettings(config, clusterShardingSettings)
  }

  def apply(config: Config, clusterShardingSettings: ClusterShardingSettings): ClusterDistributionSettings = {
    val ensureActiveInterval = config.getDuration("ensure-active-interval", TimeUnit.MILLISECONDS).milliseconds
    ClusterDistributionSettings(clusterShardingSettings, ensureActiveInterval)
  }
}

object ClusterDistribution extends ExtensionId[ClusterDistribution] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): ClusterDistribution =
    new ClusterDistribution(system)

  override def lookup = ClusterDistribution

  override def get(system: ActorSystem): ClusterDistribution = super.get(system)

  /**
   * Sent to each entity in a cluster distribution to ensure it's active.
   */
  final case class EnsureActive(entityId: EntityId)

  /**
   * The maximum number of shards we'll create to distribute the entities.
   */
  private val MaxShards = 1000
}

/**
 * Distributes a static list of entities evenly over a cluster, ensuring that they are all continuously active.
 *
 * This uses cluster sharding underneath, using a scheduled tick sent to each entity as a mechanism to ensure they are
 * active.
 *
 * Entities are cluster sharding entities, so they can discover their ID by inspecting their name. Additionally,
 * entities should handle the [[com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive]]
 * message, typically they can do nothing in response to it.
 */
class ClusterDistribution(system: ExtendedActorSystem) extends Extension {

  import ClusterDistribution._

  /**
   * Start a cluster distribution.
   *
   * @param typeName The name of the type of entity. This is used as the cluster sharding type name.
   * @param entityProps The props for the entity actor.
   * @param entityIds The entity ids to distribute over the cluster.
   * @param settings The cluster distribution settings.
   * @return the actor ref of the [[ShardRegion]] that is to be responsible for the shard
   */
  def start(
    typeName:    String,
    entityProps: Props,
    entityIds:   Set[EntityId],
    settings:    ClusterDistributionSettings
  ): ActorRef = {

    val extractEntityId: ShardRegion.ExtractEntityId = {
      case msg @ EnsureActive(entityId) => (entityId, msg)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case EnsureActive(entityId) if entityIds.size > MaxShards => Math.abs(entityId.hashCode % 1000).toString
      case EnsureActive(entityId)                               => entityId
    }

    val sharding = ClusterSharding(system)

    if (settings.clusterShardingSettings.role.forall(Cluster(system).getSelfRoles.contains)) {
      val shardRegion = sharding.start(typeName, entityProps, settings.clusterShardingSettings,
        extractEntityId, extractShardId)

      system.systemActorOf(EnsureActiveActor.props(entityIds, shardRegion, settings.ensureActiveInterval), "cluster-distribution-" + URLEncoder.encode(typeName, "utf-8"))

      shardRegion
    } else {
      sharding.startProxy(typeName, settings.clusterShardingSettings.role, extractEntityId, extractShardId)
    }
  }

}

private[cluster] object EnsureActiveActor {
  final case object Tick extends DeadLetterSuppression

  def props(entityIds: Set[EntityId], shardRegion: ActorRef, ensureActiveInterval: FiniteDuration) =
    Props(classOf[EnsureActiveActor], entityIds, shardRegion, ensureActiveInterval)
}

private[cluster] class EnsureActiveActor(entityIds: Set[EntityId], shardRegion: ActorRef,
                                         ensureActiveInterval: FiniteDuration) extends Actor {

  import EnsureActiveActor._
  import ClusterDistribution._
  import context.dispatcher

  val tick = context.system.scheduler.schedule(0.seconds, ensureActiveInterval, self, Tick)
  context.watch(shardRegion)

  override def postStop(): Unit = {
    tick.cancel()
  }

  override def receive: Receive = {
    case Tick =>
      entityIds.foreach { entityId =>
        shardRegion ! EnsureActive(entityId)
      }
    case Terminated(`shardRegion`) =>
      context.stop(self)
  }
}
