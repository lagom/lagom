/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.it

sealed trait HttpBackend {
  final val provider: String = s"play.core.server.${codeName}ServerProvider"
  val codeName: String
}

case object AkkaHttp extends HttpBackend {
  val codeName = "AkkaHttp"
}

case object Netty extends HttpBackend {
  val codeName = "Netty"
}
