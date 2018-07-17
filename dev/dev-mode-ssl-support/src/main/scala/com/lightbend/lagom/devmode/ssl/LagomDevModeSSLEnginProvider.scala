/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode.ssl

import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl._

import play.api.Logger
import play.server.api.SSLEngineProvider

import scala.util.control.NonFatal

/**
 * This class calls sslContext.createSSLEngine() with no parameters and returns the result.
 */
class LagomDevModeSSLEngineProvider(rootLagomProjectFolder: File) extends SSLEngineProvider {

  val sslContext: SSLContext = createSSLContext(rootLagomProjectFolder)

  override def createSSLEngine: SSLEngine = {
    sslContext.createSSLEngine()
  }

  def createSSLContext(rootLagomProjectFolder: File): SSLContext = {
    val file = FakeKeyStoreGenerator.keyStoreFile(rootLagomProjectFolder)
    val keyStore =
      if (!FakeKeyStoreGenerator.keyStoreFile(rootLagomProjectFolder).exists()) {
        FakeKeyStoreGenerator.buildKeystore(rootLagomProjectFolder)
      } else {
        FakeKeyStoreGenerator.load(rootLagomProjectFolder)
      }

    val keyManagerFactory: KeyManagerFactory = {

      val in = java.nio.file.Files.newInputStream(file.toPath)
      try {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        kmf.init(keyStore, Array.emptyCharArray)
        kmf
      } catch {
        case NonFatal(e) => {
          throw new Exception("Error loading HTTPS keystore from " + file.getAbsolutePath, e)
        }
      } finally {
        LagomIO.closeQuietly(in)
      }
    }

    // Configure the SSL context
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, Array(new TrustManager(keyStore)), null)
    sslContext
  }

}

object LagomDevModeSSLEngineProvider {
  private val logger = Logger(classOf[LagomDevModeSSLEngineProvider])
}

class TrustManager(trustStore: KeyStore) extends X509TrustManager {
  val nullArray = Array[X509Certificate]()

  def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}

  def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}

  def getAcceptedIssuers(): Array[X509Certificate] = {
    Array(
      trustStore
        .getCertificate(FakeKeyStoreGenerator.trustedCAAlias)
        .asInstanceOf[X509Certificate]
    )
  }
}
