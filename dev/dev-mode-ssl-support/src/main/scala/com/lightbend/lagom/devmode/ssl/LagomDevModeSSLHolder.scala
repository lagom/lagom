/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode.ssl

import java.io.File
import java.security.KeyStore

import com.typesafe.sslconfig.ssl.FakeKeyStore
import com.typesafe.sslconfig.ssl.FakeSSLTools
import com.typesafe.sslconfig.util.NoopLogger
import com.typesafe.sslconfig.{ ssl => sslconfig }
import javax.net.ssl._
import play.api.Environment

/**
 * Information required to open a file-based KeyStore (keystore or trustStore).
 *
 * @param storeFile     the file to read
 * @param storeType     type of store (e.g. JKS, JCEKS, PKCS12)
 * @param storePassword password to open the store
 */
case class KeyStoreMetadata(
    storeFile: File,
    storeType: String,
    storePassword: Array[Char]
)

class LagomDevModeSSLHolder(val rootLagomProjectFolder: File) {
  def this(env: Environment) = {
    this(env.rootPath)
  }

  private val fakeKeysStore = new sslconfig.FakeKeyStore(NoopLogger.factory())
  val keyStore: KeyStore    = fakeKeysStore.createKeyStore(rootLagomProjectFolder)

  val keyStoreMetadata: KeyStoreMetadata = KeyStoreMetadata(
    fakeKeysStore.getKeyStoreFilePath(rootLagomProjectFolder),
    FakeKeyStore.KeystoreSettings.KeystoreType,
    FakeKeyStore.KeystoreSettings.keystorePassword
  )
  val trustStoreMetadata: KeyStoreMetadata = keyStoreMetadata

  val (sslContext: SSLContext, trustManager: X509TrustManager) = FakeSSLTools.buildContextAndTrust(keyStore)
}
