/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.testkit

import java.io.File

import javax.net.ssl.SSLContext

private[lagom] object TestkitSslSetup {

  sealed trait TestkitSslSetup {
    def sslPort: Option[Int]
    def sslSettings: Map[String, AnyRef]
    def sslContext: Option[SSLContext]
  }
  case object Disabled extends TestkitSslSetup {
    val sslPort: Option[Int] = None
    val sslSettings: Map[String, AnyRef] = Map.empty[String, AnyRef]
    val sslContext: Option[SSLContext] = None
  }
  case class Enabled(
    sslPort:     Option[Int]         = Some(0),
    sslSettings: Map[String, AnyRef],
    sslContext:  Option[SSLContext]
  ) extends TestkitSslSetup

  val disabled: TestkitSslSetup = Disabled

  def enabled(keystoreFilePath: File, sslContext: SSLContext): TestkitSslSetup = {
    val sslSettings: Map[String, AnyRef] = Map(
      // See also play/core/server/LagomReloadableDevServerStart.scala
      // These configure the server
      "play.server.https.keyStore.path" -> keystoreFilePath.getAbsolutePath,
      "play.server.https.keyStore.type" -> "JKS",
      // These configure the clients (play-ws and akka-grpc)
      "ssl-config.loose.disableHostnameVerification" -> "true",
      "ssl-config.trustManager.stores.0.type" -> "JKS",
      "ssl-config.trustManager.stores.0.path" -> keystoreFilePath.getAbsolutePath
    )
    Enabled(Some(0), sslSettings, Some(sslContext))
  }
}
