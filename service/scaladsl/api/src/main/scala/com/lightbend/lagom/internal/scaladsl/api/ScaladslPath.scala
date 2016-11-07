/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.api

import com.lightbend.lagom.internal.api.{ Path, StaticPathPart }
import com.lightbend.lagom.scaladsl.api.Descriptor.{ CallId, NamedCallId, PathCallId, RestCallId }

/**
 * Path methods specific to the scaladsl
 */
object ScaladslPath {

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
