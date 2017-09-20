/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.jackson

import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

/**
 * Module that provides the default Jackson serializer factory.
 */
class JacksonModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JacksonSerializerFactory].toSelf
  )
}
