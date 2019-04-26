/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

import akka.discovery.ServiceDiscovery
import akka.discovery.ServiceDiscovery.ResolvedTarget
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Helper for implementing Akka Discovery based service locators in Lagom.
 */
private[lagom] class AkkaDiscoveryHelper(config: Config, serviceDiscovery: ServiceDiscovery)(
  implicit
  ec: ExecutionContext
) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val serviceNameMapper = new ServiceNameMapper(config)
  private val lookupTimeout = config.getDuration("lookup-timeout", TimeUnit.MILLISECONDS).millis

  def locateAll(name: String): Future[Seq[URI]] = {
    val serviceLookup = serviceNameMapper.mapLookupQuery(name)
    serviceDiscovery
      .lookup(serviceLookup.lookup, lookupTimeout)
      .map { resolved =>
        logger.debug("Retrieved addresses: {}", resolved.addresses)
        resolved.addresses.map(target => toURI(target, serviceLookup))
      }
  }

  def locate(name: String): Future[Option[URI]] = locateAll(name).map(selectRandomURI)

  private def toURI(resolvedTarget: ResolvedTarget, lookup: ServiceLookup): URI = {

    val port = resolvedTarget.port.getOrElse(-1)

    val scheme = lookup.scheme.orNull

    try {
      new URI(
        scheme, // scheme
        null, // userInfo
        resolvedTarget.host, // host
        port, // port
        null, // path
        null, // query
        null // fragment
      )
    } catch {
      case e: URISyntaxException => throw new RuntimeException(e)
    }
  }

  private def selectRandomURI(uris: Seq[URI]) = uris match {
    case Nil      => None
    case Seq(one) => Some(one)
    case many     => Some(many(ThreadLocalRandom.current().nextInt(many.size)))
  }

}
