/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server.testkit

import java.net.URI
import java.security.cert.X509Certificate

import akka.NotUsed
import play.api.mvc.{ Headers, Request }

/**
 * This is a simplified FakeRequest inspired on Play-Test's FakeRequest. Creating this simple copy here
 * avoids adding a dependency to play-test that brings in too much transitive baggage
 */
class FakeRequest(override val method: String, override val path: String) extends Request[NotUsed] {
  override def body: NotUsed = NotUsed

  override def id: Long = 42L

  override def tags: Map[String, String] = Map.empty[String, String]

  override def uri: String = new URI(path).toString

  override def version: String = "HTTP/1.1"

  override def queryString: Map[String, Seq[String]] = Map.empty[String, Seq[String]]

  override def headers: Headers = new Headers(Seq("Host" -> "localhost"))

  override def remoteAddress: String = ""

  override def secure: Boolean = false

  override def clientCertificateChain: Option[Seq[X509Certificate]] = None
}
