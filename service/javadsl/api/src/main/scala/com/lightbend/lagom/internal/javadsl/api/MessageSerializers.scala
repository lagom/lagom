/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.api

import java.lang.reflect.Type
import java.util

import akka.stream.javadsl.Source
import akka.util.ByteString
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer.{ NegotiatedDeserializer, NegotiatedSerializer }
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol
import org.pcollections.PSequence

trait PlaceholderSerializerFactory extends SerializerFactory {
  override def messageSerializerFor[MessageEntity](`type`: Type): MessageSerializer[MessageEntity, _] =
    throw new NotImplementedError("This serializer factory is only a placeholder, and cannot be used directly")
}

case object JacksonPlaceholderSerializerFactory extends PlaceholderSerializerFactory

trait UnresolvedMessageSerializer[MessageEntity] extends MessageSerializer[MessageEntity, Any] {
  override def serializerForRequest(): NegotiatedSerializer[MessageEntity, Any] =
    throw new NotImplementedError("Cannot use unresolved message serializer")

  override def deserializer(messageHeader: MessageProtocol): NegotiatedDeserializer[MessageEntity, Any] =
    throw new NotImplementedError("Cannot use unresolved message serializer")

  override def serializerForResponse(acceptedMessageHeaders: util.List[MessageProtocol]): NegotiatedSerializer[MessageEntity, Any] =
    throw new NotImplementedError("Cannot use unresolved message serializer")

  def resolve(factory: SerializerFactory, typeInfo: Type): MessageSerializer[MessageEntity, _]
}

/**
 * Placeholder serializer to instruct Lagom to find the serializer from the method ref.
 */
class MethodRefMessageSerializer[MessageEntity, WireFormat] extends MessageSerializer[MessageEntity, WireFormat] {
  override def serializerForRequest(): NegotiatedSerializer[MessageEntity, WireFormat] =
    throw new NotImplementedError("MethodRefMessageSerializer is just a placeholder")

  override def deserializer(protocol: MessageProtocol): NegotiatedDeserializer[MessageEntity, WireFormat] =
    throw new NotImplementedError("MethodRefMessageSerializer is just a placeholder")

  override def serializerForResponse(acceptedMessageProtocols: util.List[MessageProtocol]): NegotiatedSerializer[MessageEntity, WireFormat] =
    throw new NotImplementedError("MethodRefMessageSerializer is just a placeholder")
}

class UnresolvedMessageTypeSerializer[MessageEntity](val entityType: Type) extends UnresolvedMessageSerializer[MessageEntity] {
  override def resolve(factory: SerializerFactory, typeInfo: Type): MessageSerializer[MessageEntity, _] =
    factory.messageSerializerFor(entityType)
}

class UnresolvedStreamedMessageSerializer[MessageEntity](val messageType: Type) extends UnresolvedMessageSerializer[Source[MessageEntity, _]] {
  override def resolve(factory: SerializerFactory, typeInfo: Type): MessageSerializer[Source[MessageEntity, _], _] =
    factory.messageSerializerFor[MessageEntity](messageType) match {
      case strict: StrictMessageSerializer[MessageEntity] => new DelegatingStreamedMessageSerializer[MessageEntity](strict)
      case other => throw new IllegalArgumentException("Can't create streamed message serializer that delegates to " + other)
    }
}

trait PlaceholderExceptionSerializer extends ExceptionSerializer {
  override def serialize(exception: Throwable, accept: util.Collection[MessageProtocol]): RawExceptionMessage =
    throw new UnsupportedOperationException("This serializer is only a placeholder, and cannot be used directly: " + this, exception)

  override def deserialize(message: RawExceptionMessage): Throwable =
    throw new UnsupportedOperationException("This serializer is only a placeholder, and cannot be used directly: " + this + ", trying te deserialize: " + message)
}

case object JacksonPlaceholderExceptionSerializer extends PlaceholderExceptionSerializer

class DelegatingStreamedMessageSerializer[MessageEntity](delegate: StrictMessageSerializer[MessageEntity]) extends StreamedMessageSerializer[MessageEntity] {

  private class DelegatingStreamedSerializer(delegate: NegotiatedSerializer[MessageEntity, ByteString]) extends NegotiatedSerializer[Source[MessageEntity, _], Source[ByteString, _]] {
    override def protocol(): MessageProtocol = delegate.protocol()
    override def serialize(source: Source[MessageEntity, _]): Source[ByteString, _] = {
      source.asScala.map(delegate.serialize).asJava
    }
  }

  private class DelegatingStreamedDeserializer(delegate: NegotiatedDeserializer[MessageEntity, ByteString]) extends NegotiatedDeserializer[Source[MessageEntity, _], Source[ByteString, _]] {
    override def deserialize(source: Source[ByteString, _]): Source[MessageEntity, _] = {
      source.asScala.map(delegate.deserialize).asJava
    }
  }

  override def acceptResponseProtocols(): PSequence[MessageProtocol] = delegate.acceptResponseProtocols()

  override def serializerForRequest(): NegotiatedSerializer[Source[MessageEntity, _], Source[ByteString, _]] =
    new DelegatingStreamedSerializer(delegate.serializerForRequest())

  override def deserializer(messageHeader: MessageProtocol): NegotiatedDeserializer[Source[MessageEntity, _], Source[ByteString, _]] = {
    new DelegatingStreamedDeserializer(delegate.deserializer(messageHeader))
  }

  override def serializerForResponse(acceptedMessageHeaders: util.List[MessageProtocol]): NegotiatedSerializer[Source[MessageEntity, Any], Source[ByteString, Any]] = {
    new DelegatingStreamedSerializer(delegate.serializerForResponse(acceptedMessageHeaders))
  }
}
