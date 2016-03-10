/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.control.NonFatal
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.SupervisorStrategy
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.pattern.BackoffSupervisor
import com.google.inject.Injector
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSideProcessor
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import javax.inject.Inject
import javax.inject.Singleton
import com.lightbend.lagom.javadsl.persistence.AggregateEvent

@Singleton
private[lagom] class CassandraReadSideImpl @Inject() (
  system: ActorSystem, session: CassandraSession, injector: Injector
) extends CassandraReadSide {

  private val conf = system.settings.config.getConfig("lagom.persistence.read-side")
  private val minBackoff = conf.getDuration("failure-exponential-backoff.min", TimeUnit.MILLISECONDS).millis
  private val maxBackoff = conf.getDuration("failure-exponential-backoff.max", TimeUnit.MILLISECONDS).millis
  private val randomBackoffFactor = conf.getDouble("failure-exponential-backoff.random-factor")
  private val dispatcher = system.settings.config.getString("lagom.persistence.read-side.use-dispatcher")

  override def register[Event <: AggregateEvent[Event]](
    processorClass: Class[_ <: CassandraReadSideProcessor[Event]]
  ): Unit = {

    val singletonSettings = ClusterSingletonManagerSettings(system)
    val processorFactory: () => CassandraReadSideProcessor[Event] =
      () => injector.getInstance(processorClass)

    // try to create one instance to fail fast (e.g. wrong constructor)
    val tag = try {
      processorFactory().aggregateTag.tag
    } catch {
      case NonFatal(e) => throw new IllegalArgumentException("Cannot create instance of " +
        s"[${processorClass.getName}]. The class must extend PersistentEntity and have a " +
        "constructor without parameters.", e)
    }

    val processorProps = CassandraReadSideActor.props(tag, session, processorFactory).withDispatcher(dispatcher)

    val backoffProps = BackoffSupervisor.propsWithSupervisorStrategy(
      processorProps, "processor", minBackoff, maxBackoff, randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )
      .withDispatcher(dispatcher)

    val singletonProps = ClusterSingletonManager.props(backoffProps, PoisonPill, singletonSettings)
    system.actorOf(singletonProps, s"eventProcessor-${URLEncoder.encode(tag, "utf-8")}")
  }

}
