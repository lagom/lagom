/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import java.util.regex.Pattern

import akka.discovery.Lookup
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValueType
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

private[lagom] class ServiceNameMapper(config: Config) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val defaultPortName = Some(config.getString("defaults.port-name")).filter(_.nonEmpty)
  private val defaultPortProtocol = Some(config.getString("defaults.port-protocol")).filter(_.nonEmpty)
  private val defaultScheme = Some(config.getString("defaults.scheme")).filter(_.nonEmpty)

  private val serviceLookupMapping: Map[String, ServiceLookup] =
    config
      .getObject("service-name-mappings")
      .entrySet()
      .asScala
      .map { entry =>
        if (entry.getValue.valueType != ConfigValueType.OBJECT) {
          throw new IllegalArgumentException(
            s"Illegal value type in service-name-mappings: ${entry.getKey} - ${entry.getValue.valueType}")
        }
        val configEntry = entry.getValue.asInstanceOf[ConfigObject].toConfig

        def getOptionString(name: String) =
          if (configEntry.hasPath(name)) Some(configEntry.getString(name).trim)
          else None

        val lookup: Lookup =
          getOptionString("lookup")
            .map(parseSrv)
            .getOrElse(Lookup(entry.getKey, defaultPortName, defaultPortProtocol))

        // if the user didn't explicitly set a value, use the default scheme,
        // otherwise honour user settings.
        val scheme =
          getOptionString("scheme") match {
            case None => defaultScheme
            // this is the case the user explicitly set the scheme to empty string
            case Some("") => None
            case anyOther => anyOther
          }

        entry.getKey -> ServiceLookup(lookup, scheme)
      }
      .toMap

  private[lagom] def parseSrv(name: String) =
    if (LookupBuilder.isValidSrv(name)) LookupBuilder.parseSrv(name)
    else Lookup(name, defaultPortName, defaultPortProtocol)

  private[lagom] def mapLookupQuery(name: String): ServiceLookup = {
    val serviceLookup = serviceLookupMapping.getOrElse(name, ServiceLookup(parseSrv(name), defaultScheme))
    logger.debug("Lookup service '{}', mapped to {}", name: Any, serviceLookup: Any)
    serviceLookup
  }
}

private[lagom] case class ServiceLookup(lookup: Lookup, scheme: Option[String])
