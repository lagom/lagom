package com.lightbend.lagom.internal.api

import com.lightbend.lagom.javadsl.api.Descriptor.{CallId, NamedCallId, PathCallId, RestCallId}

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
