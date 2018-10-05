/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode.ssl

import java.io.File
import java.security.KeyStore

import com.typesafe.sslconfig.ssl.FakeSSLTools
import com.typesafe.sslconfig.util.NoopLogger
import com.typesafe.sslconfig.{ ssl => sslconfig }
import javax.net.ssl._
import play.api.Environment

class LagomDevModeSSLHolder(val rootLagomProjectFolder: File) {
  def this(env: Environment) = {
    this(env.rootPath)
  }

  private val fakeKeysStore = new sslconfig.FakeKeyStore(NoopLogger.factory())
  val keyStore: KeyStore = fakeKeysStore.createKeyStore(rootLagomProjectFolder)
  var keyStoreFile: File = fakeKeysStore.getKeyStoreFilePath(rootLagomProjectFolder)
  val (sslContext: SSLContext, trustManager: X509TrustManager) = FakeSSLTools.buildContextAndTrust(keyStore)
}
