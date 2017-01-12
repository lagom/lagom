/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.jackson

import com.fasterxml.jackson.annotation.{ PropertyAccessor, JsonCreator, JsonAutoDetect }
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.google.inject.Provider
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
