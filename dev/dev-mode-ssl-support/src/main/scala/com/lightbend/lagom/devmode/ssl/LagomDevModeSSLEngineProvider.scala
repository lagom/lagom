/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode.ssl

import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

import javax.net.ssl._

import com.typesafe.sslconfig.util.NoopLogger
import com.typesafe.sslconfig.{ ssl => sslconfig }

import play.server.api.SSLEngineProvider

/**
 * This class calls sslContext.createSSLEngine() with no parameters and returns the result.
 */
class LagomDevModeSSLEngineProvider(rootLagomProjectFolder: File) extends SSLEngineProvider {
  import LagomDevModeSSLEngineProvider._

  val sslContext: SSLContext = createSSLContext(rootLagomProjectFolder)

  override def createSSLEngine: SSLEngine = {
    sslContext.createSSLEngine()
  }

  def createSSLContext(rootLagomProjectFolder: File): SSLContext = {
    val keyStore = FakeKeyStore.createKeyStore(rootLagomProjectFolder)

    val kmf: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, Array.emptyCharArray)
    val kms: Array[KeyManager] = kmf.getKeyManagers

    // Configure the SSL context
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kms, Array(new TrustManager(keyStore)), null)
    sslContext
  }

}

object LagomDevModeSSLEngineProvider {
  private final val FakeKeyStore = new sslconfig.FakeKeyStore(NoopLogger.factory())
}

class TrustManager(trustStore: KeyStore) extends X509TrustManager {
  val nullArray = Array[X509Certificate]()

  def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}

  def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}

  def getAcceptedIssuers(): Array[X509Certificate] = {
    Array(
      trustStore
        .getCertificate(sslconfig.FakeKeyStore.TrustedAlias)
        .asInstanceOf[X509Certificate]
    )
  }
}
