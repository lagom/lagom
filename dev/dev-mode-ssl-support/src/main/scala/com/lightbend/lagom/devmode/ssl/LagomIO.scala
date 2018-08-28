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
