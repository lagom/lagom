/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Singleton }

import akka.actor.{ ActorSystem, SupervisorStrategy }
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterShardingSettings
import akka.pattern.BackoffSupervisor
import akka.stream.Materializer
import com.google.inject.Injector
import com.lightbend.lagom.internal.persistence.cluster.{ ClusterDistribution, ClusterDistributionSettings, ClusterStartupTask }
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, PersistentEntityRegistry, ReadSide, ReadSideProcessor }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

import scala.compat.java8.FutureConverters._

@Singleton
private[lagom] class ReadSideImpl @Inject() (
  system: ActorSystem, injector: Injector, registry: PersistentEntityRegistry
)(implicit ec: ExecutionContext, mat: Materializer) extends ReadSide {

  private val conf = system.settings.config.getConfig("lagom.persistence.read-side")
  private val minBackoff = conf.getDuration("failure-exponential-backoff.min", TimeUnit.MILLISECONDS).millis
  private val maxBackoff = conf.getDuration("failure-exponential-backoff.max", TimeUnit.MILLISECONDS).millis
  private val randomBackoffFactor = conf.getDouble("failure-exponential-backoff.random-factor")
  private val globalPrepareTimeout = conf.getDuration("global-prepare-timeout", TimeUnit.MILLISECONDS).millis
  private val role = conf.getString("run-on-role") match {
    case "" => None
    case r  => Some(r)
  }

  override def register[Event <: AggregateEvent[Event]](
    processorClass: Class[_ <: ReadSideProcessor[Event]]
  ): Unit = {

    val processorFactory: () => ReadSideProcessor[Event] =
      () => injector.getInstance(processorClass)

    registerFactory(processorFactory, processorClass)
  }

  private[lagom] def registerFactory[Event <: AggregateEvent[Event]](
    processorFactory: () => ReadSideProcessor[Event], clazz: Class[_]
  ) = {

    // Only run if we're configured to run on this role
    if (role.forall(Cluster(system).selfRoles.contains)) {
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

      val globalPrepareTask = ClusterStartupTask(
        system, s"readSideGlobalPrepare-$encodedReadSideName",
        () => processorFactory().buildHandler().globalPrepare().toScala,
        globalPrepareTimeout, role, minBackoff, maxBackoff, randomBackoffFactor
      )

      val processorProps = ReadSideActor.props(processorFactory, registry.eventStream[Event], eventClass, globalPrepareTask, globalPrepareTimeout)

      val backoffProps = BackoffSupervisor.propsWithSupervisorStrategy(
        processorProps, "processor", minBackoff, maxBackoff, randomBackoffFactor, SupervisorStrategy.stoppingStrategy
      )

      val shardingSettings = ClusterShardingSettings(system).withRole(role)

      ClusterDistribution(system).start(
        readSideName,
        backoffProps,
        entityIds.toSet,
        ClusterDistributionSettings(system).copy(clusterShardingSettings = shardingSettings)
      )
    }

  }
}
