/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.discovery

import java.util.Collections
import java.util.{ Map => JMap }
import scala.collection.JavaConverters._
import com.lightbend.lagom.internal.registry.ServiceRegistryService
import javax.inject.Inject
import java.net.URI

case class UnmanagedServices @Inject() (services: Map[String, ServiceRegistryService])
object UnmanagedServices {
  def apply(services: JMap[String, String]): UnmanagedServices = {
    val convertedServices = for ((name, url) <- services.asScala.toMap) yield {
      name -> new ServiceRegistryService(new URI(url), Collections.emptyList())
    }
    UnmanagedServices(convertedServices)
  }
}
