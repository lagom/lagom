/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.api

import com.lightbend.lagom.internal.api.Path
import com.lightbend.lagom.internal.api.StaticPathPart
import com.lightbend.lagom.javadsl.api.Descriptor.CallId
import com.lightbend.lagom.javadsl.api.Descriptor.NamedCallId
import com.lightbend.lagom.javadsl.api.Descriptor.PathCallId
import com.lightbend.lagom.javadsl.api.Descriptor.RestCallId

/**
 * Path methods specific to the javadsl
 */
object JavadslPath {

  def fromCallId(callId: CallId): Path = {
    callId match {
      case rest: RestCallId =>
        Path.parse(rest.pathPattern)
      case path: PathCallId =>
        Path.parse(path.pathPattern)
      case named: NamedCallId =>
        val name = named.name
        val path = if (name.startsWith("/")) name else "/" + name
        Path(path, Seq(StaticPathPart(path)), Nil)
    }
  }

}
