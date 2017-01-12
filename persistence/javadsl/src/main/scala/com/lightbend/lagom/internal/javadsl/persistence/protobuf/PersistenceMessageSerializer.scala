/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.protobuf

import akka.actor.ExtendedActorSystem
import akka.protobuf.ByteString
import akka.serialization.BaseSerializer
import akka.serialization.Serialization
import akka.serialization.SerializationExtension
import akka.serialization.SerializerWithStringManifest
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.javadsl.persistence.CommandEnvelope
import com.lightbend.lagom.javadsl.persistence.PersistentEntity
import com.lightbend.lagom.javadsl.persistence.PersistentEntity._
import com.lightbend.lagom.internal.javadsl.persistence.protobuf.msg.{ PersistenceMessages => pm }

/**
 * Protobuf serializer of CommandEnvelope, and other PersistentEntity
 * messages.
 */
private[lagom] class PersistenceMessageSerializer(val system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  @volatile
  private var ser: Serialization = _
  def serialization: Serialization = {
    if (ser == null) ser = SerializationExtension(system)
    ser
  }

  val CommandEnvelopeManifest = "A"
  val InvalidCommandExceptionManifest = "B"
  val UnhandledCommandExceptionManifest = "C"
  val PersistExceptionManifest = "D"
  val EnsureActiveManifest = "E"

  private val emptyByteArray = Array.empty[Byte]

  private val fromBinaryMap = collection.immutable.HashMap[String, Array[Byte] ⇒ AnyRef](
    CommandEnvelopeManifest -> commandEnvelopeFromBinary,
    InvalidCommandExceptionManifest -> invalidCommandExceptionFromBinary,
    UnhandledCommandExceptionManifest -> unhandledCommandExceptionFromBinary,
    PersistExceptionManifest -> persistExceptionFromBinary,
    EnsureActiveManifest -> ensureActiveFromBinary
  )

  override def manifest(obj: AnyRef): String = obj match {
    case _: CommandEnvelope           ⇒ CommandEnvelopeManifest
    case _: InvalidCommandException   => InvalidCommandExceptionManifest
    case _: UnhandledCommandException => UnhandledCommandExceptionManifest
    case _: PersistException          => PersistExceptionManifest
    case _: EnsureActive              => EnsureActiveManifest
    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: CommandEnvelope             ⇒ commandEnvelopeToProto(m).toByteArray
    case InvalidCommandException(msg)   => exceptionToProto(msg).toByteArray
    case UnhandledCommandException(msg) => exceptionToProto(msg).toByteArray
    case PersistException(msg)          => exceptionToProto(msg).toByteArray
    case ea: EnsureActive               => ensureActiveToProto(ea).toByteArray
    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    fromBinaryMap.get(manifest) match {
      case Some(f) ⇒ f(bytes)
      case None ⇒ throw new IllegalArgumentException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]"
      )
    }

  private def commandEnvelopeToProto(commandEnvelope: CommandEnvelope): pm.CommandEnvelope = {
    val payload = commandEnvelope.payload.asInstanceOf[AnyRef]
    val msgSerializer = serialization.findSerializerFor(payload)
    val builder = pm.CommandEnvelope.newBuilder()
      .setEntityId(commandEnvelope.entityId)
      .setEnclosedMessage(ByteString.copyFrom(msgSerializer.toBinary(payload)))
      .setSerializerId(msgSerializer.identifier)

    msgSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(payload)
        if (manifest != "")
          builder.setMessageManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (msgSerializer.includeManifest)
          builder.setMessageManifest(ByteString.copyFromUtf8(payload.getClass.getName))
    }

    builder.build()
  }

  private def commandEnvelopeFromBinary(bytes: Array[Byte]): CommandEnvelope =
    commandEnvelopeFromProto(pm.CommandEnvelope.parseFrom(bytes))

  private def commandEnvelopeFromProto(commandEnvelope: pm.CommandEnvelope): CommandEnvelope = {
    val manifest = if (commandEnvelope.hasMessageManifest) commandEnvelope.getMessageManifest.toStringUtf8 else ""
    val payload = serialization.deserialize(
      commandEnvelope.getEnclosedMessage.toByteArray,
      commandEnvelope.getSerializerId,
      manifest
    ).get
    CommandEnvelope(commandEnvelope.getEntityId, payload)
  }

  private def ensureActiveToProto(ensureActive: EnsureActive): pm.EnsureActive = {
    pm.EnsureActive.newBuilder().setEntityId(ensureActive.entityId).build()
  }

  private def ensureActiveFromBinary(bytes: Array[Byte]): EnsureActive = {
    ensureActiveFromProto(pm.EnsureActive.parseFrom(bytes))
  }

  private def ensureActiveFromProto(ensureActive: pm.EnsureActive): EnsureActive = {
    EnsureActive(ensureActive.getEntityId)
  }

  private def exceptionToProto(msg: String): pm.Exception = {
    val builder = pm.Exception.newBuilder()
    if (msg != null)
      builder.setMessage(msg)
    builder.build()
  }

  private def invalidCommandExceptionFromBinary(bytes: Array[Byte]): InvalidCommandException =
    invalidCommandExceptionFromProto(pm.Exception.parseFrom(bytes))

  private def invalidCommandExceptionFromProto(exc: pm.Exception): InvalidCommandException =
    InvalidCommandException(if (exc.hasMessage) exc.getMessage else null)

  private def unhandledCommandExceptionFromBinary(bytes: Array[Byte]): UnhandledCommandException =
    unhandledCommandExceptionFromProto(pm.Exception.parseFrom(bytes))

  private def unhandledCommandExceptionFromProto(exc: pm.Exception): UnhandledCommandException =
    UnhandledCommandException(if (exc.hasMessage) exc.getMessage else null)

  private def persistExceptionFromBinary(bytes: Array[Byte]): PersistException =
    persistExceptionFromProto(pm.Exception.parseFrom(bytes))

  private def persistExceptionFromProto(exc: pm.Exception): PersistException =
    PersistException(if (exc.hasMessage) exc.getMessage else null)
}
