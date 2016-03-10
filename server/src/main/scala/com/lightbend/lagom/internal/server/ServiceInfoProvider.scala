/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import javax.inject.{ Singleton, Inject, Provider }

import com.lightbend.lagom.javadsl.api.ServiceInfo

/**
 * Provides the service info for a service
 */
@Singleton
class ServiceInfoProvider(interface: Class[_]) extends Provider[ServiceInfo] {
  @Inject private var serverBuilder: ServerBuilder = _
  override lazy val get = serverBuilder.createServiceInfo(interface)
}
