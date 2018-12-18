package com.lightbend.lagom.internal.client

import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

import akka.discovery.Lookup
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConverters._

/**
  * Helper for implementing Akka Discovery based service locators in Lagom.
  */
private[lagom] class AkkaDiscoveryHelper(config: Config, serviceDiscovery: SimpleServiceDiscovery)(
    implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val serviceNameParser = new ServiceNameParser(config)
  private val lookupTimeout = config.getDuration("lookup-timeout", TimeUnit.MILLISECONDS).millis
  private val portNameSchemeMapping = {
    val mappings = config.getConfig("port-name-scheme-mapping")
    config
      .getObject("port-name-scheme-mapping")
      .asScala
      .map {
        case (key, value) => key -> mappings.getString(key)
      }
      .toMap
  }
  private val defaultScheme = Some(config.getString("defaults.scheme")).filter(_.nonEmpty)

  def locateAll(name: String): Future[Seq[URI]] = {
    val lookupQuery = serviceNameParser.parseLookupQuery(name)
    serviceDiscovery
      .lookup(lookupQuery, lookupTimeout)
      .map { resolved =>
        logger.debug("Retrieved addresses: {}", resolved.addresses)
        resolved.addresses.map(target => toURI(target, lookupQuery))
      }
  }

  def locate(name: String): Future[Option[URI]] = locateAll(name).map(selectRandomURI)

  private def toURI(resolvedTarget: ResolvedTarget, lookup: Lookup): URI = {
    // it's safe to call 'get' here, those have already been validated in #filterValid
    val port = resolvedTarget.port.getOrElse(-1)

    val scheme = lookup.portName
      .flatMap(portNameSchemeMapping.get)
      .orElse(defaultScheme)
      .orNull

    try {
      new URI(scheme, // scheme
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
