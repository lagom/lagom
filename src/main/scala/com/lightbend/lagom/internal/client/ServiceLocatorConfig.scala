package com.lightbend.lagom.internal.client

import java.util.Optional

import com.typesafe.config.Config
import scala.collection.JavaConverters._
import scala.collection.immutable

case class ServiceLocatorConfig(defaultPortName: String,
                                defaultScheme: String,
                                entries: immutable.Seq[ServiceConfigEntry]) {

  private val entriesMap = entries.map(e => (e.serviceName, e)).toMap

  def lookUp(serviceName: String): Option[ServiceConfigEntry] =
    entriesMap.get(serviceName)

  def lookUpJava(serviceName: String): Optional[ServiceConfigEntry] = {
    import scala.compat.java8.OptionConverters._
    lookUp(serviceName).asJava
  }

}

object ServiceLocatorConfig {
  def load(config: Config): ServiceLocatorConfig = {

    val defaultPortName =
      config.getString("lagom.akka.discovery.defaultPortName")

    val defaultScheme =
      config.getString("lagom.akka.discovery.defaultScheme")

    val portNames = config.getConfigList("lagom.akka.discovery.portNames")

    val entries =
      portNames.asScala.map { conf =>
        val serviceName = conf.getString("service")

        val portName =
          if (conf.hasPath("portName")) Option(conf.getString("portName"))
          else None

        val scheme =
          if (conf.hasPath("scheme")) Option(conf.getString("scheme"))
          else None

        ServiceConfigEntry(serviceName, portName, scheme)
      }.toList

    ServiceLocatorConfig(defaultPortName, defaultScheme, entries)
  }
}

case class ServiceConfigEntry(serviceName: String,
                              portName: Option[String],
                              scheme: Option[String])

