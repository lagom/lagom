/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.deser

import java.util.UUID

import scala.collection.immutable
import scala.collection.immutable.Seq

/**
 * A path param serializer is responsible for serializing and deserializing parameters that are extracted from and
 * formatted into paths.
 *
 * When used in URLs, a path param serializer is used both for path segments as well as query string parameters.  It is
 * expected that the serializer will consume and return singleton sequences for path segments, but may return 0 to many
 * values for query string parameters.
 */
trait PathParamSerializer[Param] {
  /**
   * Serialize the given `parameter` into path parameters.
   */
  def serialize(parameter: Param): immutable.Seq[String]

  /**
   * Deserialize the `parameters` into a deserialized parameter.
   *
   * @return The deserialized parameter.
   */
  def deserialize(parameters: immutable.Seq[String]): Param
}

object PathParamSerializer {

  abstract private class NamedPathParamSerializer[Param](val name: String) extends PathParamSerializer[Param] {
    override def toString: String = "PathParamSerializer(" + name + ")"
  }

  /**
   * Create a PathParamSerializer for required parameters.
   */
  def required[Param](name: String)(deserializeFunction: String => Param)(serializeFunction: Param => String): PathParamSerializer[Param] = new NamedPathParamSerializer[Param](name) {
    def serialize(parameter: Param): immutable.Seq[String] = immutable.Seq(serializeFunction(parameter))

    def deserialize(parameters: immutable.Seq[String]): Param = parameters.headOption match {
      case Some(parameter) => deserializeFunction(parameter)
      case None            => throw new IllegalArgumentException(name + " parameter is required")
    }
  }

  /**
   * A String path parameter serializer
   */
  implicit val StringPathParamSerializer: PathParamSerializer[String] = required("String")(identity)(identity)

  /**
   * A Long path parameter serializer
   */
  implicit val LongPathParamSerializer: PathParamSerializer[Long] = required("Long")(_.toLong)(_.toString)

  /**
   * An Int path parameter serializer
   */
  implicit val IntPathParamSerializer: PathParamSerializer[Int] = required("Int")(_.toInt)(_.toString)

  /**
   * A Boolean path parameter serializer
   */
  implicit val BooleanPathParamSerializer: PathParamSerializer[Boolean] = required("Boolean")(_.toBoolean)(_.toString)

  /**
   * A UUID path parameter serializer
   */
  implicit val UuidPathParamSerializer: PathParamSerializer[UUID] = required("UUID")(UUID.fromString)(_.toString)

  /**
   * An option path param serializer
   */
  implicit def optionPathParamSerializer[Param: PathParamSerializer]: PathParamSerializer[Option[Param]] = {
    val delegate = implicitly[PathParamSerializer[Param]]
    val name = delegate match {
      case named: NamedPathParamSerializer[Param] => s"Option[${named.name}]"
      case other                                  => s"Option($other)"
    }

    new NamedPathParamSerializer[Option[Param]](name) {
      override def serialize(parameter: Option[Param]): Seq[String] = parameter match {
        case Some(param) => delegate.serialize(param)
        case None        => Nil
      }
      override def deserialize(parameters: Seq[String]): Option[Param] = parameters match {
        case Nil    => None
        case nonNil => Some(delegate.deserialize(parameters))
      }
    }
  }
}
