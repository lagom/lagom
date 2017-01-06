/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.jackson

import scala.util.Failure
import scala.util.Success
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.event.Logging
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import akka.actor.DynamicAccess
import akka.event.LoggingAdapter
import com.typesafe.config.Config

object JacksonObjectMapperProvider extends ExtensionId[JacksonObjectMapperProvider] with ExtensionIdProvider {
  override def get(system: ActorSystem): JacksonObjectMapperProvider = super.get(system)

  override def lookup = JacksonObjectMapperProvider

  override def createExtension(system: ExtendedActorSystem): JacksonObjectMapperProvider =
    new JacksonObjectMapperProvider(
      system.settings.config,
      system.dynamicAccess,
      Some(Logging.getLogger(system, classOf[JacksonObjectMapperProvider]))
    )
}

/**
 * Creates Jackson `ObjectMapper` with sensible defaults and modules configured
 * in `lagom.serialization.json.jackson-modules`.
 */
class JacksonObjectMapperProvider(
  config:        Config,
  dynamicAccess: DynamicAccess,
  log:           Option[LoggingAdapter]
) extends Extension {

  val objectMapper: ObjectMapper = {
    import scala.collection.JavaConverters._

    val mapper = new ObjectMapper

    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

    val configuredModules = config.getStringList(
      "lagom.serialization.json.jackson-modules"
    ).asScala
    val modules: Seq[Module] =
      if (configuredModules.contains("*"))
        ObjectMapper.findModules(dynamicAccess.classLoader).asScala
      else {
        configuredModules.flatMap { fqcn =>
          dynamicAccess.createInstanceFor[Module](fqcn, Nil) match {
            case Success(m) => Some(m)
            case Failure(e) =>
              log.foreach(_.error(e, s"Could not load configured Jackson module [$fqcn], " +
                "please verify classpath dependencies or amend the configuration " +
                "[lagom.serialization.json.jackson-modules]. Continuing " +
                "without this module."))
              None
          }
        }
      }

    modules.foreach { module =>
      if (module.isInstanceOf[ParameterNamesModule])
        // ParameterNamesModule needs a special case for the constructor to ensure that single-parameter
        // constructors are handled the same way as constructors with multiple parameters.
        // See https://github.com/FasterXML/jackson-module-parameter-names#delegating-creator
        mapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
      else {
        mapper.registerModule(module)
      }
      log.foreach(_.debug("Registered Jackson module [{}]", module.getClass.getName))
    }

    mapper
  }
}
