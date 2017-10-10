/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server.testkit

import java.net.URI

import akka.NotUsed
import play.api.libs.typedmap.TypedMap
import play.api.mvc.request.{ RemoteConnection, RequestTarget }
import play.api.mvc.{ Headers, Request }
import play.core.parsers.FormUrlEncodedParser

/**
 * This is a simplified FakeRequest inspired on Play-Test's FakeRequest. Creating this simple copy here
 * avoids adding a dependency to play-test that brings in too much transitive baggage
 */
class FakeRequest(override val method: String, path: String) extends Request[NotUsed] {
  override def body: NotUsed = NotUsed

  override def connection: RemoteConnection = RemoteConnection(remoteAddressString = "127.0.0.1", secure = false, clientCertificateChain = None)

  private val _path = path

  override def target: RequestTarget = new RequestTarget {
    override lazy val uri: URI = new URI(uriString)

    override def uriString: String = _path

    override lazy val path: String = uriString.split('?').take(1).mkString
    override lazy val queryMap: Map[String, Seq[String]] = FormUrlEncodedParser.parse(queryString)
  }

  override def version: String = "HTTP/1.1"

  override def headers: Headers = new Headers(Seq("Host" -> "localhost"))

  override def attrs: TypedMap = TypedMap.empty
}
