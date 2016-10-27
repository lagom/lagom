/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.server

import javax.inject.{ Inject, Provider, Singleton }

import com.lightbend.lagom.javadsl.api.ServiceInfo

/**
 * Provides the service info for a service
 */
@Singleton
class ServiceInfoProvider(interface: Class[_]) extends Provider[ServiceInfo] {
  @Inject private var serverBuilder: JavadslServerBuilder = _
  override lazy val get = serverBuilder.createServiceInfo(interface)
}
