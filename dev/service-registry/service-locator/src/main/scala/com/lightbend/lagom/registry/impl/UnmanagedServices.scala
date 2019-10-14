/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.net.URI
import java.util.Collections
import java.util.{ Map => JMap }

import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import com.lightbend.lagom.javadsl.api.ServiceAcl
import javax.inject.Inject

import scala.collection.JavaConverters._

case class UnmanagedServices @Inject() (services: Map[String, ServiceRegistryService])

object UnmanagedServices {
  def apply(services: JMap[String, String]): UnmanagedServices = {
    val convertedServices = for ((name, url) <- services.asScala.toMap) yield {
      name -> new ServiceRegistryService(new URI(url))
    }
    UnmanagedServices(convertedServices)
  }
}
