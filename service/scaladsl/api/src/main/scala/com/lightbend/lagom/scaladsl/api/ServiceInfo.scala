/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

sealed trait ServiceInfo {
  /**
   * The name of this service.
   */
  val serviceName: String
}

object ServiceInfo {

  def apply(name: String): ServiceInfo = ServiceInfoImpl(name)

  private case class ServiceInfoImpl(serviceName: String) extends ServiceInfo
}
