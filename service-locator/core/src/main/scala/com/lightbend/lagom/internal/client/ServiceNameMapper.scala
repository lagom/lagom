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

  private val serviceLookupRegex = Some(config.getString("service-lookup-regex"))
    .filter(_.nonEmpty)
    .map(Pattern.compile)

  private val defaultPortName = Some(config.getString("defaults.port-name")).filter(_.nonEmpty)
  private val defaultPortProtocol = Some(config.getString("defaults.port-protocol")).filter(_.nonEmpty)
  private val serviceNameSuffix = config.getString("service-name-suffix")

  private val portNameSchemeMapping = {
    val mappings = config.getConfig("port-name-scheme-mapping")
    config
      .getObject("port-name-scheme-mapping")
      .asScala
      .map {
        case (key, _) => key -> mappings.getString(key)
      }
      .toMap
  }
  private val defaultScheme = Some(config.getString("defaults.scheme")).filter(_.nonEmpty)

  private val serviceLookupMapping = config
    .getObject("service-name-mappings")
    .entrySet()
    .asScala
    .map { entry =>
      if (entry.getValue.valueType != ConfigValueType.OBJECT) {
        throw new IllegalArgumentException(
          s"Illegal value type in service-name-mappings: ${entry.getKey} - ${entry.getValue.valueType}")
      }
      val c = entry.getValue.asInstanceOf[ConfigObject].toConfig

      def get(name: String) = if (c.hasPath(name)) Some(c.getString(name)) else None

      val serviceName = get("service-name").getOrElse(entry.getKey) + serviceNameSuffix
      val portName = get("port-name").orElse(defaultPortName)
      val portProtocol = get("port-protocol").orElse(defaultPortProtocol)
      val scheme = get("scheme")
        .orElse(portName.flatMap(portNameSchemeMapping.get))
        .orElse(defaultScheme)
      entry.getKey -> ServiceNameMapping(entry.getKey, serviceName, portName, portProtocol, scheme)
    }
    .toMap

  private[lagom] def mapLookupQuery(name: String): ServiceLookup = {
    // First attempt to construct lookup using configured mappings
    val serviceLookup = serviceLookupMapping.get(name) match {
      case Some(mapping) =>
        ServiceLookup(Lookup(mapping.serviceName, mapping.portName, mapping.portProtocol), mapping.scheme)

      case None =>
        // Next attempt to construct lookup using the service lookup regex
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

        ServiceLookup(lookup, lookup.portName.flatMap(portNameSchemeMapping.get).orElse(defaultScheme))
    }

    logger.debug("Lookup service '{}', mapped to {}", name: Any, serviceLookup: Any)
    serviceLookup
  }
}

private[lagom] case class ServiceNameMapping(name: String,
                                             serviceName: String,
                                             portName: Option[String],
                                             portProtocol: Option[String],
                                             scheme: Option[String])

private[lagom] case class ServiceLookup(lookup: Lookup, scheme: Option[String])
