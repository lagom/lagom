/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
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
  val locatableServices: Map[String, immutable.Seq[ServiceAcl]]
}

object ServiceInfo {

  def apply(name: String, locatableServices: Map[String, immutable.Seq[ServiceAcl]]): ServiceInfo =
    ServiceInfoImpl(name, locatableServices)

  private case class ServiceInfoImpl(serviceName: String, locatableServices: Map[String, immutable.Seq[ServiceAcl]]) extends ServiceInfo
}
