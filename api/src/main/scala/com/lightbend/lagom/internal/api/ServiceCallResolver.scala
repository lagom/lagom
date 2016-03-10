/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.lang.reflect._

import akka.stream.javadsl.Source
import com.lightbend.lagom.javadsl.api.deser.{ IdSerializer, MessageSerializer, SerializerFactory }

class ServiceCallResolver(
  idSerializers:      Map[Type, IdSerializer[_]],
  messageSerializers: Map[Type, MessageSerializer[_, _]],
  serializerFactory:  SerializerFactory
) extends SerializerFactory {

  def resolveIdSerializer[T](idSerializer: IdSerializer[T], typeInfo: Option[Type] = None) = idSerializer match {
    case unresolved: UnresolvedIdSerializer[T] => unresolved.resolve(this, typeInfo)
    case resolved                              => resolved
  }

  def idSerializerFor[T](idType: Type): IdSerializer[T] = {
    // First try a direct type lookup, then fall back to a raw class lookup
    val serializer = idSerializers.get(idType).asInstanceOf[Option[IdSerializer[T]]] getOrElse {
      idType match {
        case clazz: Class[_] =>
          // in future, fallback to reflection based
          throw new IllegalArgumentException(s"Don't know how to serialize ID $clazz")
        case param: ParameterizedType  => idSerializerFor(param.getRawType)
        case wild: WildcardType        => throw new IllegalArgumentException(s"Cannot serialize wildcard types: $wild")
        case variable: TypeVariable[_] => throw new IllegalArgumentException(s"Cannot serialize type variables: $variable")
        case array: GenericArrayType   => ??? // todo
        case other                     => throw new IllegalArgumentException(s"Unknown type: $other")
      }
    }

    resolveIdSerializer(serializer.asInstanceOf[IdSerializer[T]], Some(idType))
  }

  def resolveMessageSerializer[T, W](messageSerializer: MessageSerializer[T, W], typeInfo: Option[Type] = None) = messageSerializer match {
    case unresolved: UnresolvedMessageSerializer[T] => unresolved.resolve(this, typeInfo)
    case resolved                                   => resolved
  }

  def messageSerializerFor[T](messageType: Type): MessageSerializer[T, _] = {
    // First, try looking up the registered serializers
    // If that fails, see if it's a stream, and if so, return a serializer for that
    // Otherwise, use the configured serializer factory.
    val serializer = registeredMessageSerializerFor(messageType)
      .orElse(streamSerializerFor(messageType))
      .getOrElse(serializerFactory.messageSerializerFor(messageType))

    resolveMessageSerializer(serializer.asInstanceOf[MessageSerializer[T, _]], Some(messageType))
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
