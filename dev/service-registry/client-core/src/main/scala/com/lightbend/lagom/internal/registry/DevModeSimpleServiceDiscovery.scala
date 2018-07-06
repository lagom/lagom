/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.registry

import java.net.URI

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery._
import akka.discovery.{ Lookup, ServiceDiscovery, SimpleServiceDiscovery }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }

private[lagom] class DevModeSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {
  private val clientPromise = Promise[ServiceRegistryClient]

  implicit private val ec: ExecutionContext = system.dispatcher

  def setServiceRegistryClient(client: ServiceRegistryClient): Unit = clientPromise.success(client)

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    for {
      client <- clientPromise.future
      uris <- client.locateAll(lookup.serviceName)
    } yield Resolved(lookup.serviceName, uris.map(toResolvedTarget))

  private def toResolvedTarget(uri: URI) = ResolvedTarget(uri.getHost, optionalPort(uri.getPort))

  private def optionalPort(port: Int): Option[Int] = if (port < 0) None else Some(port)
}

private[lagom] object DevModeSimpleServiceDiscovery {
  def apply(system: ActorSystem): DevModeSimpleServiceDiscovery =
    ServiceDiscovery(system)
      .loadServiceDiscovery(classOf[DevModeSimpleServiceDiscovery].getName)
      .asInstanceOf[DevModeSimpleServiceDiscovery]
}
