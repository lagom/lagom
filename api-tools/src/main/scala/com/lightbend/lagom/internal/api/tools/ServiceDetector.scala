/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.tools

import com.lightbend.lagom.internal.spi.{ ServiceAcl, ServiceDescription, ServiceDiscovery }
import com.typesafe.config.ConfigFactory
import play.api._
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.collection.immutable
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

/**
 * A service detector locates the services of a Lagom project.
 */
object ServiceDetector {

  private val ServiceDiscoveryKey = "lagom.tools.service-discovery"
  private val ApplicationLoaderKey = "play.application.loader"

  val log = Logger(this.getClass)

  implicit val serviceAclsWrites: Writes[ServiceAcl] = (
    (__ \ "method").writeNullable[String] and
    (__ \ "pathPattern").writeNullable[String]
  ).apply(sa => (sa.method().asScala, sa.pathPattern().asScala))

  implicit val serviceDescriptionWrites: Writes[ServiceDescription] = (
    (__ \ "name").write[String] and
    (__ \ "acls").write[immutable.Seq[ServiceAcl]]
  ).apply(sd => (sd.name, sd.acls.asScala.to[immutable.Seq]))

  /**
   * Retrieves the service names and acls for the current Lagom project
   * of all services.
   *
   *
   * @param classLoader The class loader should contain a sbt project in the classpath
   *                    for which the services should be resolved.
   * @return a JSON array of [[com.lightbend.lagom.internal.spi.ServiceDescription]] objects.
   */
  def services(classLoader: ClassLoader): String = {
    val config = ConfigFactory.load(classLoader)
    val serviceDiscoveryClassName = if (config.hasPath(ServiceDiscoveryKey)) {
      config.getString(ServiceDiscoveryKey)
    } else {
      config.getString(ApplicationLoaderKey)
    }

    log.debug("Loading service discovery class: " + serviceDiscoveryClassName)

    val serviceDiscoverClass = classLoader.loadClass(serviceDiscoveryClassName)
    val castServiceDiscoveryClass = serviceDiscoverClass.asSubclass(classOf[ServiceDiscovery])
    val serviceDiscovery = castServiceDiscoveryClass.newInstance()
    val services = serviceDiscovery.discoverServices(classLoader).asScala.to[immutable.Seq]
    Json.stringify(Json.toJson(services))
  }
}
