/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import scala.concurrent.duration._
import scala.compat.java8.FutureConverters._
import scala.collection.immutable
import akka.persistence.cassandra.ConfigSessionProvider
import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.net.InetSocketAddress

import com.lightbend.lagom.internal.persistence.ServiceLocatorHolder

import scala.util.control.NoStackTrace
import play.api.Logger

import scala.concurrent.Promise

private[lagom] class ServiceLocatorSessionProvider(system: ActorSystem, config: Config) extends ConfigSessionProvider(system, config) {

  private val log = Logger(getClass)

  override def lookupContactPoints(clusterId: String)(implicit ec: ExecutionContext): Future[immutable.Seq[InetSocketAddress]] = {
    ServiceLocatorHolder(system).serviceLocatorEventually flatMap { serviceLocator =>
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
    }
  }
}

private[lagom] class NoContactPointsException(msg: String) extends RuntimeException(msg) with NoStackTrace
