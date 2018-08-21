/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode.ssl

import java.io.{ Closeable, File, IOException }
import java.math.BigInteger
import java.security.cert.{ X509Certificate, Certificate }
import java.security.interfaces.RSAPublicKey
import java.security._
import java.util.Date

import play.api.Logger
import sun.security.x509._

private[lagom] object LagomIO {
  // copy/pasted from play.utils.PlayIO
  private val logger = Logger(this.getClass)

  def closeQuietly(closeable: Closeable) = {
    try {
      if (closeable != null) {
        closeable.close()
      }
    } catch {
      case e: IOException => logger.warn("Error closing stream", e)
    }
  }
}

/**
 * A fake key store generator for Dev mode SSL.
 *
 * TODO: export CA to CA.crt so users can easily import into the browser and trust it.
 */
object FakeKeyStoreGenerator {
  private val GeneratedKeyStore = "target/dev-mode/generated.keystore"
  private val ExportedCACert = "target/dev-mode/ca.crt"
  private val ExportedCert = "target/dev-mode/service.crt"
  val trustedCAAlias = "playgeneratedCAtrusted"
  val trustedAlias = "playgeneratedtrusted"

  /**
   * @param appPath a file descriptor to the root folder of the project (the root, not a particular module).
   */
  def keyStoreFile(appPath: File) = new File(appPath, GeneratedKeyStore)

  private val DnNameCA = "CN=localhost-CA, OU=Unit Testing, O=Mavericks, L=Lagom Base 1, ST=Cyberspace, C=CY"
  private val DnName = "CN=localhost, OU=Unit Testing, O=Mavericks, L=Lagom Base 1, ST=Cyberspace, C=CY"
  private val SignatureAlgorithmOID = AlgorithmId.sha256WithRSAEncryption_oid
  private val SignatureAlgorithmName = "SHA256withRSA"

  private def shouldGenerate(keyStoreFile: File): Boolean = {
    import scala.collection.JavaConverters._

    if (!keyStoreFile.exists()) {
      return true
    }

    // Should regenerate if we find an unacceptably weak key in there.
    val store = KeyStore.getInstance("JKS")
    val in = java.nio.file.Files.newInputStream(keyStoreFile.toPath)
    try {
      store.load(in, "".toCharArray)
    } finally {
      LagomIO.closeQuietly(in)
    }
    store.aliases().asScala.exists { alias =>
      Option(store.getCertificate(alias)).exists(c => certificateTooWeak(c))
    }
  }

  private def certificateTooWeak(c: java.security.cert.Certificate): Boolean = {
    val key: RSAPublicKey = c.getPublicKey.asInstanceOf[RSAPublicKey]
    key.getModulus.bitLength < 2048 || c.asInstanceOf[X509CertImpl].getSigAlgName != SignatureAlgorithmName
  }

  def load(appPath: File): KeyStore = {
    val keyStore: KeyStore = KeyStore.getInstance("JKS")
    val file = keyStoreFile(appPath)

    val in = java.nio.file.Files.newInputStream(file.toPath)
    try {
      keyStore.load(in, Array.emptyCharArray)
    } finally {
      LagomIO.closeQuietly(in)
    }
    keyStore
  }

  def buildKeystore(appPath: File): KeyStore = {
    val file = keyStoreFile(appPath)
    file.getParentFile.mkdirs()
    val keyStore: KeyStore = {
      val freshKeyStore: KeyStore = generateKeyStore
      val out = java.nio.file.Files.newOutputStream(file.toPath)
      try {
        freshKeyStore.store(out, "".toCharArray)
      } finally {
        LagomIO.closeQuietly(out)
      }
      freshKeyStore
    }
    keyStore
  }

  /**
   * Generate a fresh KeyStore object in memory. This KeyStore
   * is not saved to disk. If you want that, then call `keyManagerFactory`.
   *
   * This method has has `private[play]` access so it can be used for
   * testing.
   */
  private def generateKeyStore: KeyStore = {
    // Create a new KeyStore
    val keyStore: KeyStore = KeyStore.getInstance("JKS")

    // Generate the key pair
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048) // 2048 is the NIST acceptable key length until 2030
    val cakeyPair = keyPairGenerator.generateKeyPair()
    val keyPair = keyPairGenerator.generateKeyPair()

    // Generate a self signed certificate
    val cacert: X509Certificate = createCA(cakeyPair)
    val cert: X509Certificate = createCertificate(keyPair, cakeyPair)

    // Create the key store, first set the store pass
    keyStore.load(null, "".toCharArray)
    keyStore.setKeyEntry("playgeneratedCA", keyPair.getPrivate, "".toCharArray, Array(cacert))
    keyStore.setCertificateEntry(trustedCAAlias, cacert)
    keyStore.setKeyEntry("playgenerated", keyPair.getPrivate, "".toCharArray, Array(cert))
    keyStore.setCertificateEntry(trustedAlias, cert)
    keyStore
  }

  private def createCA(keyPair: KeyPair): X509Certificate = {
    val certInfo = new X509CertInfo()

    // Serial number and version
    certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, new SecureRandom())))
    certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3))

    // Validity
    val validFrom = new Date()
    val validTo = new Date(validFrom.getTime + 50l * 365l * 24l * 60l * 60l * 1000l)
    val validity = new CertificateValidity(validFrom, validTo)
    certInfo.set(X509CertInfo.VALIDITY, validity)

    // Subject and issuer
    val owner = new X500Name(DnNameCA)
    certInfo.set(X509CertInfo.SUBJECT, owner)
    certInfo.set(X509CertInfo.ISSUER, owner)

    // Key and algorithm
    certInfo.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic))
    val algorithm = new AlgorithmId(SignatureAlgorithmOID)
    certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algorithm))

    val caExtension =
      new CertificateExtensions
    caExtension.set(BasicConstraintsExtension.NAME, new BasicConstraintsExtension( /* isCritical */ true, /* isCA */ true, 0))
    certInfo.set(X509CertInfo.EXTENSIONS, caExtension)

    // Create a new certificate and sign it
    val cert = new X509CertImpl(certInfo)
    cert.sign(keyPair.getPrivate, SignatureAlgorithmName)

    // Since the signature provider may have a different algorithm ID to what we think it should be,
    // we need to reset the algorithm ID, and resign the certificate
    val actualAlgorithm = cert.get(X509CertImpl.SIG_ALG).asInstanceOf[AlgorithmId]
    certInfo.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, actualAlgorithm)
    val newCert = new X509CertImpl(certInfo)
    newCert.sign(keyPair.getPrivate, SignatureAlgorithmName)
    newCert
  }

  private def createCertificate(keyPair: KeyPair, cakeyPair: KeyPair): X509Certificate = {
    val certInfo = new X509CertInfo()

    // Serial number and version
    certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, new SecureRandom())))
    certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3))

    // Validity
    val validFrom = new Date()
    val validTo = new Date(validFrom.getTime + 50l * 365l * 24l * 60l * 60l * 1000l)
    val validity = new CertificateValidity(validFrom, validTo)
    certInfo.set(X509CertInfo.VALIDITY, validity)

    // Subject and issuer
    val ca = new X500Name(DnNameCA)
    certInfo.set(X509CertInfo.ISSUER, ca)
    val owner = new X500Name(DnName)
    certInfo.set(X509CertInfo.SUBJECT, owner)

    // Key and algorithm
    certInfo.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic))
    val algorithm = new AlgorithmId(SignatureAlgorithmOID)
    certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algorithm))

    // Create a new certificate and sign it
    val cert = new X509CertImpl(certInfo)
    cert.sign(keyPair.getPrivate, SignatureAlgorithmName)

    // Since the signature provider may have a different algorithm ID to what we think it should be,
    // we need to reset the algorithm ID, and resign the certificate
    val actualAlgorithm = cert.get(X509CertImpl.SIG_ALG).asInstanceOf[AlgorithmId]
    certInfo.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, actualAlgorithm)
    val newCert = new X509CertImpl(certInfo)
    newCert.sign(cakeyPair.getPrivate, SignatureAlgorithmName)
    newCert
  }
}
