/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.registry

import java.net.InetAddress
import java.net.URI

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery._
import akka.discovery.Discovery
import akka.discovery.Lookup
import akka.discovery.ServiceDiscovery

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

private[lagom] class DevModeServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {
  private val clientPromise = Promise[ServiceRegistryClient]

  private implicit val ec: ExecutionContext = system.dispatcher

  def setServiceRegistryClient(client: ServiceRegistryClient): Unit = clientPromise.success(client)

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    for {
      client <- clientPromise.future
      uris   <- client.locateAll(lookup.serviceName, lookup.portName)
    } yield Resolved(lookup.serviceName, uris.map(toResolvedTarget))

  private def toResolvedTarget(uri: URI) =
    ResolvedTarget(
      uri.getHost,
      optionalPort(uri.getPort),
      // we don't have the InetAddress, but instead of using None
      // we default to localhost as such we can use it for Akka Cluster Bootstrap eventually
      address = Some(InetAddress.getLocalHost)
    )

  private def optionalPort(port: Int): Option[Int] = if (port < 0) None else Some(port)
}

private[lagom] object DevModeServiceDiscovery {
  def apply(system: ActorSystem): DevModeServiceDiscovery =
    Discovery(system)
      .loadServiceDiscovery("lagom-dev-mode")
      .asInstanceOf[DevModeServiceDiscovery]
}
