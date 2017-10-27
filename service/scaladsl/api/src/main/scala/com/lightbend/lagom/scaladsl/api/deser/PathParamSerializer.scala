/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.deser

import java.util.UUID

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.language.higherKinds

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

object PathParamSerializer extends DefaultPathParamSerializers

trait DefaultPathParamSerializers extends LowPriorityPathParamSerializers {

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
   * An Double path parameter serializer
   */
  implicit val DoublePathParamSerializer: PathParamSerializer[Double] = required("Double")(_.toDouble)(_.toString)

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
  implicit def optionPathParamSerializer[Param](implicit delegate: PathParamSerializer[Param]): PathParamSerializer[Option[Param]] = {

    val name = delegate match {
      case named: NamedPathParamSerializer[_] => s"Option[${named.name}]"
      case other                              => s"Option($other)"
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

trait LowPriorityPathParamSerializers {

  sealed abstract class NamedPathParamSerializer[Param](val name: String) extends PathParamSerializer[Param] {
    override def toString: String = "PathParamSerializer(" + name + ")"
  }

  /**
   * A traversable path param serializer
   */
  implicit def traversablePathParamSerializer[CC[X] <: Traversable[X], Param: PathParamSerializer](implicit delegate: PathParamSerializer[Param], bf: CanBuildFrom[CC[_], Param, CC[Param]]): PathParamSerializer[CC[Param]] = {

    val name = delegate match {
      case named: NamedPathParamSerializer[_] => s"Traversable[${named.name}]"
      case other                              => s"Traversable($other)"
    }

    new NamedPathParamSerializer[CC[Param]](name) {
      override def serialize(parameter: CC[Param]): Seq[String] = parameter.flatMap(delegate.serialize).to[Seq]
      override def deserialize(parameters: Seq[String]): CC[Param] = {
        val builder = bf()
        builder.sizeHint(parameters)
        builder ++= parameters.map(param => delegate.deserialize(Seq(param)))
        builder.result()
      }
    }
  }
}
