/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.jackson

import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

/**
 * Module that provides the default Jackson serializer factory.
 */
class JacksonModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JacksonSerializerFactory].toSelf,
    bind[JacksonExceptionSerializer].toSelf
  )
}
