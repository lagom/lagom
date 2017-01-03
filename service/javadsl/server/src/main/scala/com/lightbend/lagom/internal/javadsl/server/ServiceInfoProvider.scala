/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.server

import java.util.Optional
import javax.inject.{ Inject, Provider, Singleton }

import com.lightbend.lagom.javadsl.api.ServiceInfo

import scala.collection.mutable

/**
 * Provides the service info for a service
 */
@Singleton
class ServiceInfoProvider(primaryServiceInterface: Class[_], secondaryServices: Array[Class[_]]) extends Provider[ServiceInfo] {
  @Inject private var serverBuilder: JavadslServerBuilder = _
  override lazy val get = {
    serverBuilder.createServiceInfo(primaryServiceInterface, secondaryServices)
  }
}
