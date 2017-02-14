/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client

import javax.inject.{ Inject, Singleton }

import com.google.inject.Provider
import com.lightbend.lagom.internal.javadsl.api.{ JacksonPlaceholderExceptionSerializer, JacksonPlaceholderSerializerFactory, ServiceReader }
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.jackson.{ JacksonExceptionSerializer, JacksonSerializerFactory }
import play.api.Environment

@Singleton
class ServiceClientLoader @Inject() (
  jacksonSerializerFactory:   JacksonSerializerFactory,
  jacksonExceptionSerializer: JacksonExceptionSerializer, environment: Environment,
  implementor: JavadslServiceClientImplementor
) {
  def loadServiceClient[T](interface: Class[T]): T = {
    if (classOf[Service].isAssignableFrom(interface)) {
      val descriptor = ServiceReader.readServiceDescriptor(
        environment.classLoader,
        interface.asSubclass(classOf[Service])
      )
      val resolved = ServiceReader.resolveServiceDescriptor(descriptor, environment.classLoader,
        Map(JacksonPlaceholderSerializerFactory -> jacksonSerializerFactory),
        Map(JacksonPlaceholderExceptionSerializer -> jacksonExceptionSerializer))
      implementor.implement(interface, resolved)
    } else {
      throw new IllegalArgumentException("Don't know how to load services that don't implement Service")
    }
  }
}

@Singleton
class ServiceClientProvider[T](interface: Class[T]) extends Provider[T] {
  @Inject private var loader: ServiceClientLoader = _
  lazy val get = loader.loadServiceClient(interface)
}
