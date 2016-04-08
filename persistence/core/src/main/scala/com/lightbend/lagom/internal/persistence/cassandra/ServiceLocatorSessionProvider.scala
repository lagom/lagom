/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import scala.concurrent.duration._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.collection.immutable
import akka.persistence.cassandra.ConfigSessionProvider
import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.lightbend.lagom.javadsl.api.ServiceLocator
import akka.actor.Extension
import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ExtendedActorSystem
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.net.InetSocketAddress
import scala.util.control.NoStackTrace
import play.api.Logger
import scala.concurrent.Promise

private[lagom] class ServiceLocatorSessionProvider(system: ActorSystem, config: Config) extends ConfigSessionProvider(system, config) {

  private val log = Logger(getClass)

  override def lookupContactPoints(clusterId: String)(implicit ec: ExecutionContext): Future[immutable.Seq[InetSocketAddress]] = {
    val result = ServiceLocatorHolder(system).serviceLocator match {
      case Some(serviceLocator) =>
        serviceLocator.locate(clusterId).toScala.map { location =>
          if (location.isPresent) {
            val uri = location.get
            log.debug(s"Found Cassandra contact points: $uri")
            require(uri.getHost != null, s"missing host in $uri for Cassandra contact points $clusterId")
            require(uri.getPort != -1, s"missing port in $uri for Cassandra contact points $clusterId")
            List(new InetSocketAddress(uri.getHost, uri.getPort))
          } else {
            // fail the future
            throw new NoContactPointsException(s"No contact points for [$clusterId]")
          }
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

private[lagom] class NoContactPointsException(msg: String) extends RuntimeException(msg) with NoStackTrace

private[lagom] object ServiceLocatorHolder extends ExtensionId[ServiceLocatorHolder] with ExtensionIdProvider {
  override def get(system: ActorSystem): ServiceLocatorHolder = super.get(system)

  override def lookup = ServiceLocatorHolder

  override def createExtension(system: ExtendedActorSystem): ServiceLocatorHolder =
    new ServiceLocatorHolder
}

private[lagom] class ServiceLocatorHolder extends Extension {
  @volatile private var _serviceLocator: Option[ServiceLocator] = None

  def serviceLocator: Option[ServiceLocator] = _serviceLocator

  def setServiceLocator(locator: ServiceLocator): Unit =
    _serviceLocator = Some(locator)
}
