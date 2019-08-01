/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.projection

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializerWithStringManifest
import akka.serialization.BaseSerializer
import com.lightbend.lagom.internal.projection.protobuf.msg.{ ProjectionMessages => pm }

private[lagom] class ProjectionMessageSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  val EnsureActiveManifest = "E"

  override def manifest(obj: AnyRef): String = ???

  def toBinary(obj: AnyRef): Array[Byte] = ???

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = ???

}
