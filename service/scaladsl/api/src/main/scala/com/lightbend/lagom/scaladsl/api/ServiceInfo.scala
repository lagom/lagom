/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api

import scala.collection.immutable

sealed trait ServiceInfo {
  /**
   * The name of this service.
   */
  val serviceName: String

  /**
   * ACLs for this service.
   */
  val acls: Iterable[ServiceAcl]
}

object ServiceInfo {
  @deprecated("Use apply(String, Seq[ServiceAcl]) version", "1.6.0")
  def apply(name: String, locatableServices: Map[String, immutable.Seq[ServiceAcl]]): ServiceInfo =
    new ServiceInfoImpl(name, locatableServices.values.flatten)

  def apply(name: String, acls: immutable.Seq[ServiceAcl]): ServiceInfo = ServiceInfoImpl(name, acls)

  private case class ServiceInfoImpl(serviceName: String, override val acls: Iterable[ServiceAcl]) extends ServiceInfo
}
