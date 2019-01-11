/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import java.util.regex.Pattern

import akka.discovery.Lookup
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

private[lagom] class ServiceNameParser(config: Config) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val serviceLookupRegex = Some(config.getString("service-lookup-regex"))
    .filter(_.nonEmpty)
    .map(Pattern.compile)

  private val defaultPortName = Some(config.getString("defaults.port-name")).filter(_.nonEmpty)
  private val defaultPortProtocol = Some(config.getString("defaults.port-protocol")).filter(_.nonEmpty)
  private val serviceNameSuffix = config.getString("service-name-suffix")

  private[lagom] def parseLookupQuery(name: String) = {
    val lookup = serviceLookupRegex match {
      case Some(pattern) =>
        val matcher = pattern.matcher(name)
        if (matcher.matches()) {
          val serviceName = matcher.group("service")
          if (serviceName == null || serviceName.isEmpty) {
            throw new IllegalArgumentException(
              "Service lookup regex did not contain a named capturing group called " +
                "'service', or that group matched an empty string.")
          }

          def groupOrElse(group: String, default: Option[String]) =
            Option(matcher.group(group)).filter(_.nonEmpty).orElse(default)

          Lookup(
            serviceName + serviceNameSuffix,
            groupOrElse("portName", defaultPortName),
            groupOrElse("portProtocol", defaultPortProtocol)
          )
        } else {
          Lookup(name + serviceNameSuffix, defaultPortName, defaultPortProtocol)
        }
      case None => Lookup(name + serviceNameSuffix, defaultPortName, defaultPortProtocol)
    }

    logger.debug("Lookup service '{}', parsed as {}", name: Any, lookup: Any)
    lookup
  }
}
