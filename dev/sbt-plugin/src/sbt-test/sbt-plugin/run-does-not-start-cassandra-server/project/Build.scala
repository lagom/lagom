/* 
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
import sbt._

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.Properties
import java.net.HttpURLConnection
import java.io.{BufferedReader, InputStreamReader}

object DevModeBuild {

  val ConnectTimeout = 10000
  val ReadTimeout = 10000

  def isCassandraServerReachable(): Boolean = makeRequest("http://localhost:4000") { conn =>
    true
  }

  private def makeRequest[T](uri: String)(body: HttpURLConnection => T): T = {
    var conn: java.net.HttpURLConnection = null
    try {
      val url = new java.net.URL(uri)
      conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setConnectTimeout(ConnectTimeout)
      conn.setReadTimeout(ReadTimeout)
      conn.getResponseCode // we make this call just to block until a response is returned.
      body(conn)
    }
    finally if(conn != null) conn.disconnect()
  }
}
