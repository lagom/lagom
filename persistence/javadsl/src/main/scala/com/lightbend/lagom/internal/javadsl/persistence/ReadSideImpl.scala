/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence

import java.net.URLEncoder
import java.util.Optional
import javax.inject.{ Inject, Provider, Singleton }

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterShardingSettings
import akka.stream.Materializer
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.{ ClusterDistribution, ClusterDistributionSettings, ClusterStartupTask }
import com.lightbend.lagom.javadsl.persistence._
import com.typesafe.config.Config
import play.api.inject.Injector

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
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

  protected val name: Optional[String] = Optional.empty()

  override def register[Event <: AggregateEvent[Event]](
    processorClass: Class[_ <: ReadSideProcessor[Event]]
  ): Unit = {

    val processorFactory: () => ReadSideProcessor[Event] =
      () => injector.instanceOf(processorClass)

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

      val readSideName = name.asScala.fold("")(_ + "-") + dummyProcessor.readSideName()
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
        ReadSideActor.props(
          config,
          eventClass,
          globalPrepareTask,
          registry.eventStream[Event],
          processorFactory
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
