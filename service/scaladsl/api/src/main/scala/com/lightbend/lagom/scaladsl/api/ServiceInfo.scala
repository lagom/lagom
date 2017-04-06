/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import scala.collection.immutable

sealed trait ServiceInfo {
  /**
   * The name of this service.
   */
  val serviceName: String

  /**
   * All the locatable services and their ACLs
   */
  @deprecated("Lagom will no longer support multiple locatable descriptors per service.", "1.3.2")
  val locatableServices: Map[String, immutable.Seq[ServiceAcl]]

  /**
   * ACLs for this service.
   */
  val acls: Iterable[ServiceAcl]
}

object ServiceInfo {

  def apply(name: String, locatableServices: Map[String, immutable.Seq[ServiceAcl]]): ServiceInfo =
    ServiceInfoImpl(name, locatableServices)

  private case class ServiceInfoImpl(serviceName: String, locatableServices: Map[String, immutable.Seq[ServiceAcl]]) extends ServiceInfo {
    override val acls: Iterable[ServiceAcl] = locatableServices.values.flatten
  }

}
