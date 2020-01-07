/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import akka.discovery.Lookup
import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValueType
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

private[lagom] class ServiceNameMapper(config: Config) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val defaultPortName     = readConfigValue(config, "defaults.port-name").toOption
  private val defaultPortProtocol = readConfigValue(config, "defaults.port-protocol").toOption
  private val defaultScheme       = readConfigValue(config, "defaults.scheme").toOption

  private sealed trait ConfigValue {
    def toOption =
      this match {
        case NonEmpty(v) => Some(v)
        case _           => None
      }
  }
  private object ConfigValue {
    def apply(value: String) =
      if (value.trim.isEmpty) Empty
      else NonEmpty(value.trim)
  }
  private case object Undefined              extends ConfigValue
  private case object Empty                  extends ConfigValue
  private case class NonEmpty(value: String) extends ConfigValue

  private def readConfigValue(config: Config, name: String): ConfigValue =
    if (config.hasPathOrNull(name)) {
      if (config.getIsNull(name)) Empty
      else ConfigValue(config.getString(name))
    } else Undefined

  /**
   * Reads an optional config.
   * If the user didn't explicitly set a value, use the passed default, otherwise honour user settings
   */
  private def readOptionalConfigValue(config: Config, name: String, defaultValue: Option[String]): Option[String] =
    readConfigValue(config, name) match {
      case Undefined => defaultValue
      // this is the case the user explicitly set the scheme to empty string
      case Empty           => None
      case NonEmpty(value) => Option(value)
    }

  private val serviceLookupMapping: Map[String, ServiceLookup] =
    config
      .getObject("service-name-mappings")
      .entrySet()
      .asScala
      .map { entry =>
        if (entry.getValue.valueType != ConfigValueType.OBJECT) {
          throw new IllegalArgumentException(
            s"Illegal value type in service-name-mappings: ${entry.getKey} - ${entry.getValue.valueType}"
          )
        }
        val configEntry = entry.getValue.asInstanceOf[ConfigObject].toConfig

        // read config values for portName, portProtocol and scheme
        // when not explicitly overwritten by used, uses default values
        val portName     = readOptionalConfigValue(configEntry, "port-name", defaultPortName)
        val portProtocol = readOptionalConfigValue(configEntry, "port-protocol", defaultPortProtocol)
        val scheme       = readOptionalConfigValue(configEntry, "scheme", defaultScheme)

        val lookup: Lookup =
          readConfigValue(configEntry, "lookup").toOption
            .map(name => parseSrv(name, portName, portProtocol))
            .getOrElse(Lookup(entry.getKey, portName, portProtocol))

        entry.getKey -> ServiceLookup(lookup, scheme)
      }
      .toMap

  private def parseSrv(name: String, portName: Option[String], portProtocol: Option[String]) =
    if (Lookup.isValidSrv(name)) Lookup.parseSrv(name)
    else Lookup(name, portName, portProtocol)

  private[lagom] def mapLookupQuery(name: String): ServiceLookup = {
    val serviceLookup = serviceLookupMapping.getOrElse(
      name,
      ServiceLookup(parseSrv(name, defaultPortName, defaultPortProtocol), defaultScheme)
    )
    logger.debug("Lookup service '{}', mapped to {}", name: Any, serviceLookup: Any)
    serviceLookup
  }
}

private[lagom] case class ServiceLookup(lookup: Lookup, scheme: Option[String])
