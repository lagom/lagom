/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.tools

import com.lightbend.lagom.javadsl.api.{ Descriptor }
import play.api.libs.json.{ Json, Format }
import scala.collection.immutable
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

/**
 * The service description is only used internally to create a JSON representation based on this structure.
 */
private object ServiceDescription {

  /**
   * Creates a service description from a descriptor
   */
  def apply(descriptor: Descriptor): ServiceDescription =
    ServiceDescription(
      name = descriptor.name(),
      acls = descriptor.acls().asScala.toVector.map(acl =>
        Acl(
          method = acl.method().asScala.map(_.toString),
          pathPattern = acl.pathRegex().asScala
        ))
    )

  implicit val format: Format[ServiceDescription] = Json.format
}

private final case class ServiceDescription(name: String, acls: immutable.Seq[Acl])

private object Acl {
  implicit val format: Format[Acl] = Json.format
}

private final case class Acl(method: Option[String], pathPattern: Option[String])
