/* 
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

import scala.annotation.tailrec
import java.net.HttpURLConnection
import java.io.{BufferedReader, InputStreamReader}

object DevModeBuild {

  val ConnectTimeout = 10000
  val ReadTimeout = 10000

  def callFoo(): String = makeRequest("http://localhost:8000/services/%2Ffooservice") { conn =>
    val br = new BufferedReader(new InputStreamReader((conn.getInputStream())))
    val fooAddress = Stream.continually(br.readLine()).takeWhile(_ != null).mkString("\n").trim()
    makeRequest(fooAddress+"/foo") { conn =>
      val br = new BufferedReader(new InputStreamReader((conn.getInputStream())))
      Stream.continually(br.readLine()).takeWhile(_ != null).mkString("\n").trim()
    }
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
