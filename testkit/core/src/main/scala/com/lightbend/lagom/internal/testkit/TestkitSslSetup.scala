/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.testkit

import javax.net.ssl.SSLContext
import com.lightbend.lagom.devmode.ssl.KeyStoreMetadata

private[lagom] object TestkitSslSetup {
  sealed trait TestkitSslSetup {
    def sslPort: Option[Int]

    def sslSettings: Map[String, AnyRef]

    def clientSslContext: Option[SSLContext]
  }

  case object Disabled extends TestkitSslSetup {
    val sslPort: Option[Int]                 = None
    val sslSettings: Map[String, AnyRef]     = Map.empty[String, AnyRef]
    val clientSslContext: Option[SSLContext] = None
  }

  case class Enabled(
      sslPort: Option[Int] = Some(0),
      sslSettings: Map[String, AnyRef],
      clientSslContext: Option[SSLContext]
  ) extends TestkitSslSetup

  val disabled: TestkitSslSetup = Disabled

  /**
   *
   * @param serverKeyStoreFile keyStore to setup the server
   * @param trustStoreFile     trustStore for the clients
   * @param clientSslContext   SSLContext to create SSL clients
   * @return
   */
  def enabled(
      keyStoreMetadata: KeyStoreMetadata,
      trustStoreMetadata: KeyStoreMetadata,
      clientSslContext: SSLContext
  ): TestkitSslSetup = {
    val sslSettings: Map[String, AnyRef] = Map(
      // See also play/core/server/LagomReloadableDevServerStart.scala
      // These configure the server
      "play.server.https.keyStore.path"     -> keyStoreMetadata.storeFile.getAbsolutePath,
      "play.server.https.keyStore.type"     -> keyStoreMetadata.storeType,
      "play.server.https.keyStore.password" -> String.valueOf(keyStoreMetadata.storePassword),
      // These configure the clients (play-ws and akka-grpc)
      "ssl-config.loose.disableHostnameVerification" -> "true",
      "ssl-config.trustManager.stores.0.path"        -> trustStoreMetadata.storeFile.getAbsolutePath,
      "ssl-config.trustManager.stores.0.type"        -> trustStoreMetadata.storeType,
      "ssl-config.trustManager.stores.0.password"    -> String.valueOf(trustStoreMetadata.storePassword)
    )
    Enabled(Some(0), sslSettings, Some(clientSslContext))
  }
}
