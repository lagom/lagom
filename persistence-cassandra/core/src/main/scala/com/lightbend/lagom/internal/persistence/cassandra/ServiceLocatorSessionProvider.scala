/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.net.InetSocketAddress
import java.net.URI

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.actor.ActorSystem
import akka.persistence.cassandra.ConfigSessionProvider
import com.typesafe.config.Config
import play.api.Logger

/**
 * Internal API
 */
private[lagom] final class ServiceLocatorSessionProvider(system: ActorSystem, config: Config)
  extends ConfigSessionProvider(system, config) {

  private val log = Logger(getClass)

  override def lookupContactPoints(clusterId: String)(implicit ec: ExecutionContext): Future[immutable.Seq[InetSocketAddress]] = {
    val result = ServiceLocatorHolder(system).serviceLocator match {
      case Some(serviceLocator) =>
        serviceLocator.locate(clusterId).map {
          case Some(uri) =>
            log.debug(s"Found Cassandra contact points: $uri")
            require(uri.getHost != null, s"missing host in $uri for Cassandra contact points $clusterId")
            require(uri.getPort != -1, s"missing port in $uri for Cassandra contact points $clusterId")
            List(new InetSocketAddress(uri.getHost, uri.getPort))
          case None =>
            // fail the future
            throw new NoContactPointsException(s"No contact points for [$clusterId]")
        }

      case None =>
        val promise = Promise[immutable.Seq[InetSocketAddress]]()

        // retry a few times to best effort to avoid failure due to startup race condition between Akka Persistence
        // and the binding of the ServiceLocator in the  Guice module
        def tryAgain(count: Int): Unit = {
          if (count == 0)
            promise.failure(new IllegalStateException("ServiceLocator is not bound"))
          else {
            system.scheduler.scheduleOnce(200.millis) {
              if (ServiceLocatorHolder(system).serviceLocator.isDefined)
                promise.completeWith(lookupContactPoints(clusterId))
              else
                tryAgain(count - 1)
            }
          }
        }

        tryAgain(10)

        promise.future
    }

    result.onFailure {
      case e =>
        log.warn(s"Could not find Cassandra contact points, due to: ${e.getMessage}")
    }

    result
  }

}

private[lagom] final class NoContactPointsException(msg: String) extends RuntimeException(msg) with NoStackTrace
