/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.jackson

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.serialization.BaseSerializer
import akka.serialization.SerializationExtension
import akka.serialization.SerializerWithStringManifest
import akka.serialization.jackson.JacksonMigration
import com.lightbend.lagom.serialization.Jsonable

/**
 * Placeholder for Lagom 1.5.x JacksonSerializer.
 * Needed for deserialization of messages during rolling update, and old data (events, snapshots)
 * that were serialized with the Lagom 1.5.x JacksonSerializer.
 */
private[lagom] class OldJacksonJsonSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  private val log = Logging.getLogger(system, getClass)
  private lazy val jacksonJsonSerializer: SerializerWithStringManifest = {
    SerializationExtension(system)
      .serializerFor(classOf[Jsonable])
      .asInstanceOf[SerializerWithStringManifest]
  }

  if (system.settings.config.hasPath("lagom.serialization.json.migrations")) {
    throw new IllegalStateException(
      "JacksonJsonSerializer migrations defined in " +
        s"'lagom.serialization.json.migrations' must be rewritten as [${classOf[JacksonMigration].getName}] " +
        "and defined in config 'akka.serialization.jackson.migrations'."
    )
  }

  override def manifest(obj: AnyRef): String =
    jacksonJsonSerializer.manifest(obj)

  override def toBinary(obj: AnyRef): Array[Byte] = {
    log.warning(
      "[{}] shouldn't be used for serialization of [{}]. Bind it to 'jackson-json' instead in " +
        "'akka.actor.serialization-bindings' configuration.",
      getClass.getName,
      obj.getClass.getName
    )

    jacksonJsonSerializer.toBinary(obj)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    jacksonJsonSerializer.fromBinary(bytes, manifest)

}
