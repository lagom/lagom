/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.api

import java.lang.reflect.{ ParameterizedType, Type }
import java.util.Optional

import com.lightbend.lagom.javadsl.api.deser.{ PathParamSerializer, PathParamSerializers }
import org.pcollections.{ PSequence, TreePVector }

import scala.compat.java8.FunctionConverters._

trait UnresolvedPathParamSerializer[Param] extends PathParamSerializer[Param] {
  def serialize(parameter: Param) = throw new NotImplementedError("Cannot use unresolved path param serializer to serialize path params")

  def deserialize(parameters: PSequence[String]) = throw new NotImplementedError("Cannot use unresolved path param serializer to deserialize path params")

  def resolve(resolver: ServiceCallResolver, typeInfo: Type): PathParamSerializer[Param]
}

class UnresolvedTypePathParamSerializer[Param] extends UnresolvedPathParamSerializer[Param] {
  override def resolve(resolver: ServiceCallResolver, typeInfo: Type): PathParamSerializer[Param] = {
    resolver.pathParamSerializerFor(typeInfo, typeInfo)
  }
}

class UnresolvedOptionalPathParamSerializer[Param] extends UnresolvedPathParamSerializer[Optional[Param]] {
  override def resolve(resolver: ServiceCallResolver, typeInfo: Type): PathParamSerializer[Optional[Param]] = {
    typeInfo match {
      case paramType: ParameterizedType if paramType.getRawType == classOf[Optional[_]] =>
        val wrappedType = paramType.getActualTypeArguments.apply(0)
        val subTypeSerializer = resolver.pathParamSerializerFor[Param](wrappedType, wrappedType)
        PathParamSerializers.optional[Param](
          wrappedType.getTypeName,
          (subTypeSerializer.deserialize _).compose((p: String) => TreePVector.singleton(p)).asJava,
          (subTypeSerializer.serialize _).andThen {
          case single if single.size() == 1 => single.get(0)
          case other                        => throw new IllegalStateException("Can only wrap an Optional serializer around a path param serializer that produces exactly one parameter")
        }.asJava
        )
      case _ => throw new IllegalArgumentException("Unresolved optional path param serializer can only be resolved against ParamaterizedType descriptors for the Optional class. This serializer was resolved against: " + typeInfo)
    }
  }
}
