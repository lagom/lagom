/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.api

import java.lang.reflect._

import akka.stream.javadsl.Source
import com.lightbend.lagom.javadsl.api.deser.{ PathParamSerializer, MessageSerializer, SerializerFactory }

abstract class CallResolver(
  messageSerializers: Map[Type, MessageSerializer[_, _]],
  serializerFactory:  SerializerFactory
) extends SerializerFactory {

  def resolveMessageSerializer[T, W](messageSerializer: MessageSerializer[T, W], typeInfo: Type): MessageSerializer[T, _] = messageSerializer match {
    case methodRef: MethodRefMessageSerializer[T, _] => messageSerializerFor[T](typeInfo)
    case unresolved: UnresolvedMessageSerializer[T]  => unresolved.resolve(this, typeInfo)
    case resolved                                    => resolved
  }

  def messageSerializerFor[T](messageType: Type): MessageSerializer[T, _] = {
    // First, try looking up the registered serializers
    // If that fails, see if it's a stream, and if so, return a serializer for that
    // Otherwise, use the configured serializer factory.
    val serializer = registeredMessageSerializerFor(messageType)
      .orElse(streamSerializerFor(messageType))
      .getOrElse(serializerFactory.messageSerializerFor(messageType))

    resolveMessageSerializer(serializer.asInstanceOf[MessageSerializer[T, _]], messageType)
  }

  private def streamSerializerFor[T](streamType: Type): Option[MessageSerializer[T, _]] = {
    streamType match {
      case param: ParameterizedType if param.getRawType == classOf[Source[_, _]] =>
        val messageType = param.getActualTypeArguments()(0)
        Some(new UnresolvedStreamedMessageSerializer[Any](messageType).asInstanceOf[MessageSerializer[T, _]])
      case _ => None
    }
  }

  private def registeredMessageSerializerFor[T](messageType: Type): Option[MessageSerializer[T, _]] = {
    messageSerializers.get(messageType).asInstanceOf[Option[MessageSerializer[T, _]]] orElse {
      messageType match {
        case param: ParameterizedType => registeredMessageSerializerFor[T](param.getRawType)
        case clazz: Class[_]          => None
      }
    }
  }
}

private object ServiceCallResolver {
  private val primitiveClassMap = Map[Class[_], Class[_]](
    classOf[Boolean] -> classOf[java.lang.Boolean],
    classOf[Byte] -> classOf[java.lang.Byte],
    classOf[Short] -> classOf[java.lang.Short],
    classOf[Char] -> classOf[java.lang.Character],
    classOf[Int] -> classOf[java.lang.Integer],
    classOf[Long] -> classOf[java.lang.Long],
    classOf[Float] -> classOf[java.lang.Float],
    classOf[Double] -> classOf[java.lang.Double]
  )
}

class ServiceCallResolver(
  idSerializers:      Map[Type, PathParamSerializer[_]],
  messageSerializers: Map[Type, MessageSerializer[_, _]],
  serializerFactory:  SerializerFactory
) extends CallResolver(messageSerializers, serializerFactory) {

  def resolvePathParamSerializer[Param](pathParamSerializer: PathParamSerializer[Param], typeInfo: Type) = pathParamSerializer match {
    case unresolved: UnresolvedPathParamSerializer[Param] => unresolved.resolve(this, typeInfo)
    case resolved                                         => resolved
  }

  def pathParamSerializerFor[Param](lookupType: Type, typeInfo: Type): PathParamSerializer[Param] = {
    // First try a direct type lookup, then fall back to a raw class lookup
    val serializer = idSerializers.get(lookupType).asInstanceOf[Option[PathParamSerializer[Param]]] getOrElse {
      lookupType match {
        case clazz: Class[_] if clazz.isPrimitive => pathParamSerializerFor(ServiceCallResolver.primitiveClassMap(clazz), typeInfo)
        case clazz: Class[_] =>
          // we've already looked up by class, so we're not going to get any further - fail
          throw new IllegalArgumentException(s"Don't know how to serialize path parameter $clazz")
        case param: ParameterizedType  => pathParamSerializerFor(param.getRawType, typeInfo)
        case wild: WildcardType        => throw new IllegalArgumentException(s"Cannot serialize wildcard types: $wild")
        case variable: TypeVariable[_] => throw new IllegalArgumentException(s"Cannot serialize type variables: $variable")
        case array: GenericArrayType   => ??? // todo
        case other                     => throw new IllegalArgumentException(s"Unknown type: $other")
      }
    }

    resolvePathParamSerializer(serializer.asInstanceOf[PathParamSerializer[Param]], typeInfo)
  }
}

class TopicCallResolver(
  messageSerializers: Map[Type, MessageSerializer[_, _]],
  serializerFactory:  SerializerFactory
) extends CallResolver(messageSerializers, serializerFactory)
