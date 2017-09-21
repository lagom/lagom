/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Provider, Singleton }

import akka.actor.{ ActorSystem, SupervisorStrategy }
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion.{ EntityId, StartEntity }
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import com.google.inject.Injector
import com.lightbend.lagom.internal.javadsl.persistence.ReadSideTagHolderActor.{ GetTag, CachedTag }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.{ ClusterDistribution, ClusterDistributionSettings, ClusterStartupTask }
import com.lightbend.lagom.javadsl.persistence._
import com.typesafe.config.Config
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class ReadSideConfigProvider @Inject() (configuration: Config) extends Provider[ReadSideConfig] {

  lazy val get = {
    ReadSideConfig(configuration.getConfig("lagom.persistence.read-side"))
  }
}

@Singleton
private[lagom] class ReadSideImpl @Inject() (
  system: ActorSystem, config: ReadSideConfig, injector: Injector, registry: PersistentEntityRegistry
)(implicit ec: ExecutionContext, mat: Materializer) extends ReadSide {

  override def register[Event <: AggregateEvent[Event]](
    processorClass: Class[_ <: ReadSideProcessor[Event]]
  ): Unit = {

    val processorFactory: () => ReadSideProcessor[Event] =
      () => injector.getInstance(processorClass)

    registerFactory(processorFactory, processorClass)
  }

  private[lagom] def registerFactory[Event <: AggregateEvent[Event]](
    processorFactory: () => ReadSideProcessor[Event],
    clazz:            Class[_]
  ) = {

    // Only run if we're configured to run on this role
    if (config.role.forall(Cluster(system).selfRoles.contains)) {
      // try to create one instance to fail fast (e.g. wrong constructor)
      val dummyProcessor = try {
        processorFactory()
      } catch {
        case NonFatal(e) => throw new IllegalArgumentException("Cannot create instance of " +
          s"[${clazz.getName}]", e)
      }

      val readSideName = dummyProcessor.readSideName()
      val encodedReadSideName = URLEncoder.encode(readSideName, "utf-8")
      val tags = dummyProcessor.aggregateTags().asScala
      val entityIds = tags.map(_.tag)
      val eventClass = tags.headOption match {
        case Some(tag) => tag.eventType
        case None      => throw new IllegalArgumentException(s"ReadSideProcessor ${clazz.getName} returned 0 tags")
      }

      val globalPrepareTask: ClusterStartupTask =
        ClusterStartupTask(
          system,
          s"readSideGlobalPrepare-$encodedReadSideName",
          () => processorFactory().buildHandler().globalPrepare().toScala,
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
        entityIds.toSet,
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
