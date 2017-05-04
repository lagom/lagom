/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
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
    ServiceLocatorHolder(system).serviceLocatorEventually flatMap { serviceLocator =>
      serviceLocator.locate(clusterId).map {
        case Some(uri) =>
          log.debug(s"Found Cassandra contact points: $uri")
          require(uri.getHost != null, s"missing host in $uri for Cassandra contact points $clusterId")
          require(uri.getPort != -1, s"missing port in $uri for Cassandra contact points $clusterId")
          List(new InetSocketAddress(uri.getHost, uri.getPort))
        case _ => throw new NoContactPointsException(s"No contact points for [$clusterId]")
      }
    }
  }
}

private[lagom] final class NoContactPointsException(msg: String) extends RuntimeException(msg) with NoStackTrace
