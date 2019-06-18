/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.jackson

import akka.actor.ActorSystem
import akka.serialization.jackson.JacksonMigration
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module
import javax.inject.Inject

/**
 * Module that provides the default Jackson serializer factory.
 */
class JacksonModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JacksonMigrationCheck].toSelf.eagerly(),
    bind[JacksonSerializerFactory].toSelf,
    bind[JacksonExceptionSerializer].toSelf
  )
}

private[lagom] class JacksonMigrationCheck @Inject()(system: ActorSystem) {
  if (system.settings.config.hasPath("lagom.serialization.json.migrations")) {
    throw new IllegalStateException(
      "JacksonJsonSerializer migrations defined in " +
        s"'lagom.serialization.json.migrations' must be rewritten as [${classOf[JacksonMigration].getName}] " +
        "and defined in config 'akka.serialization.jackson.migrations'."
    )
  }
}
