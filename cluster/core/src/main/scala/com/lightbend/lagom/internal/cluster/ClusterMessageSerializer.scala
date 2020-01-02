/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cluster

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializerWithStringManifest
import akka.serialization.BaseSerializer
import com.lightbend.lagom.internal.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.cluster.protobuf.msg.{ ClusterMessages => cm }

private[lagom] class ClusterMessageSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {
  val EnsureActiveManifest = "E"

  override def manifest(obj: AnyRef): String = obj match {
    case _: EnsureActive => EnsureActiveManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case ea: EnsureActive => ensureActiveToProto(ea).toByteArray
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  private def ensureActiveToProto(ensureActive: EnsureActive): cm.EnsureActive = {
    cm.EnsureActive.newBuilder().setEntityId(ensureActive.entityId).build()
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case EnsureActiveManifest => ensureActiveFromBinary(bytes)
    case _ =>
      throw new IllegalArgumentException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]"
      )
  }

  private def ensureActiveFromBinary(bytes: Array[Byte]): EnsureActive = {
    ensureActiveFromProto(cm.EnsureActive.parseFrom(bytes))
  }

  private def ensureActiveFromProto(ensureActive: cm.EnsureActive): EnsureActive = {
    EnsureActive(ensureActive.getEntityId)
  }
}
