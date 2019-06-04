/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

import play.dev.filewatch.FileWatchService
import play.sbt.run.toLoggerProxy
import sbt._

import javax.net.ssl.SSLContext
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.Properties

// This is an almost verbatim copy from Play's
// https://github.com/playframework/playframework/blob/master/framework/src/sbt-plugin/src/sbt-test/play-sbt-plugin/generated-keystore/project/Build.scala
// I think some parts could be trimmed but keeping the (almost) verbatim copy will ease future consolidation.
// Changes compared to Play's version:
//  - had to replace `path` with `url` in `verifyResourceContains`
object DevModeBuild {

  def jdk7WatchService = Def.setting {
    FileWatchService.jdk7(Keys.sLog.value)
  }

  def jnotifyWatchService = Def.setting {
    FileWatchService.jnotify(Keys.target.value)
  }

  // Using 30 max attempts so that we can give more chances to
  // the file watcher service. This is relevant when using the
  // default JDK watch service which does uses polling.
  val MaxAttempts = 30
  val WaitTime    = 500L

  val ConnectTimeout = 10000
  val ReadTimeout    = 10000

  private val trustAllManager = {
    val manager = new X509TrustManager() {
      def getAcceptedIssuers: Array[X509Certificate]                                = null
      def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
      def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
    }
    Array[TrustManager](manager)
  }
  @tailrec
  def verifyResourceContains(
      url: String,
      status: Int,
      assertions: Seq[String],
      attempts: Int,
      headers: (String, String)*
  ): Unit = {
    println(s"Attempt $attempts at $url")
    val messages = ListBuffer.empty[String]
    try {
      val sc = SSLContext.getInstance("SSL")
      sc.init(null, trustAllManager, null)
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)

      val jnUrl = new java.net.URL(url)
      val conn  = jnUrl.openConnection().asInstanceOf[java.net.HttpURLConnection]
      conn.setConnectTimeout(ConnectTimeout)
      conn.setReadTimeout(ReadTimeout)

      headers.foreach(h => conn.setRequestProperty(h._1, h._2))

      if (status == conn.getResponseCode) messages += s"Resource at $url returned $status as expected"
      else throw new RuntimeException(s"Resource at $url returned ${conn.getResponseCode} instead of $status")

      val is = if (conn.getResponseCode >= 400) conn.getErrorStream else conn.getInputStream

      // The input stream may be null if there's no body
      val contents = if (is != null) {
        val c = IO.readStream(is)
        is.close()
        c
      } else ""
      conn.disconnect()

      assertions.foreach { assertion =>
        if (contents.contains(assertion)) messages += s"Resource at $url contained $assertion"
        else throw new RuntimeException(s"Resource at $url didn't contain '$assertion':\n$contents")
      }

      messages.foreach(println)
    } catch {
      case e: Exception =>
        println(s"Got exception: $e")
        if (attempts < MaxAttempts) {
          Thread.sleep(WaitTime)
          verifyResourceContains(url, status, assertions, attempts + 1, headers: _*)
        } else {
          messages.foreach(println)
          println(s"After $attempts attempts:")
          throw e
        }
    }
  }
}
