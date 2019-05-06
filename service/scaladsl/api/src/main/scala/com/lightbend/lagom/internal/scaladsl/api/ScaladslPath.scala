/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.api

import com.lightbend.lagom.internal.api.Path
import com.lightbend.lagom.internal.api.StaticPathPart
import com.lightbend.lagom.scaladsl.api.Descriptor.CallId
import com.lightbend.lagom.scaladsl.api.Descriptor.NamedCallId
import com.lightbend.lagom.scaladsl.api.Descriptor.PathCallId
import com.lightbend.lagom.scaladsl.api.Descriptor.RestCallId

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
