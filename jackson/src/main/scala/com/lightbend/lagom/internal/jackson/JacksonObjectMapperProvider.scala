/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.jackson

import java.util.concurrent.atomic.AtomicReference
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
import java.lang.reflect.Type

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

  // ParameterNamesModule can't be used (and is not needed) for the Immutables classes
  private val (objectMapper: ObjectMapper, objectMapperWithParameterNames: ObjectMapper) = {
    val mapper1 = new ObjectMapper
    val mapper2 = new ObjectMapper
    import scala.collection.JavaConverters._

    mapper1.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper2.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    mapper1.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
    mapper2.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

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
        mapper2.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
      else {
        mapper1.registerModule(module)
        mapper2.registerModule(module)
      }
      log.foreach(_.debug("Registered Jackson module [{}]", module.getClass.getName))
    }
    (mapper1, mapper2)
  }

  private val hasJsonCreatorCache = new AtomicReference[Map[Class[_], Boolean]](Map.empty)

  private def hasJsonCreator(clazz: Class[_]): Boolean = {
    def updateCache(cache: Map[Class[_], Boolean], key: Class[_], value: Boolean): Boolean = {
      hasJsonCreatorCache.compareAndSet(cache, cache.updated(key, value)) ||
        updateCache(hasJsonCreatorCache.get, key, value) // recursive, try again
    }

    val cache = hasJsonCreatorCache.get
    cache.get(clazz) match {
      case Some(value) => value
      case None =>
        val value = clazz.getDeclaredMethods.exists(_.isAnnotationPresent(classOf[JsonCreator]))
        updateCache(cache, clazz, value)
        value
    }
  }

  /**
   * Retrieve the `ObjectMapper` for a given class. There are two shared instances
   * and one or the other is selected depending on if the class has a `JsonCreator`
   * annotation or not. The instance that is returned for classes with `JsonCreator`
   * is not configured with the `ParameterNamesModule`, because it can't be used
   * (and is not needed) for Immutables classes which has `JsonCreator`
   * annotations.
   */
  def objectMapper(forType: Type): ObjectMapper = {
    forType match {
      case clazz: Class[_] =>
        if (hasJsonCreator(clazz)) objectMapper else objectMapperWithParameterNames
      case _ =>
        objectMapper
    }
  }

}

