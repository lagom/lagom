/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cluster

import akka.actor.ExtendedActorSystem
import akka.serialization.{ BaseSerializer, SerializerWithStringManifest }
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor.Execute

private[lagom] class ClusterStartupTaskSerializer(val system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  val ExecuteManifest = "E"

  override def manifest(obj: AnyRef) = obj match {
    case Execute => ExecuteManifest
    case _       => throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def toBinary(obj: AnyRef) = obj match {
    case Execute => Array.emptyByteArray
    case _       => throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String) = manifest match {
    case `ExecuteManifest` => Execute
    case _ => throw new IllegalArgumentException(
      s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]"
    )
  }
}
