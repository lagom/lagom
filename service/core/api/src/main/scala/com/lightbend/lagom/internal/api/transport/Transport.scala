/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.transport

import java.net.URI
import java.nio.charset.Charset
import java.security.Principal

import akka.util.ByteString

import scala.collection.immutable
import scala.io.Codec

// INTERNAL API
// This provides a bridge between Lagom's core transport APIs, and the javadsl/scaladsl APIs
private[lagom] case class LagomMessageProtocol(contentType: Option[String], charset: Option[String], version: Option[String]) {
  def toContentTypeHeader: Option[String] = {
    contentType.map(ct =>
      charset.fold(ct)(charset => s"$ct; charset=$charset"))
  }

  def isUtf8 = charset.exists(cs => Charset.forName(cs).equals(Codec.UTF8.charSet))

  def isText = charset.isDefined
}

private[lagom] object LagomMessageProtocol {

  def fromContentTypeHeader(contentType: Option[String]): LagomMessageProtocol = {
    contentType.fold(LagomMessageProtocol(None, None, None)) { ct =>
      val parts = ct.split(";")
      val justContentType = parts(0)
      val charset = parts.collectFirst {
        case charsetPart if charsetPart.startsWith("charset=") => charsetPart.split("=", 2)(1)
      }
      LagomMessageProtocol(Some(justContentType), charset, None)
    }
  }
}

private[lagom] sealed trait LagomMessageHeader {
  val protocol: LagomMessageProtocol
  /**
   * A map of lower case string header keys, to original header key/value pairs
   */
  val headers: Map[String, immutable.Seq[(String, String)]]
}

private[lagom] case class LagomRequestHeader(
  method:                    String,
  uri:                       URI,
  protocol:                  LagomMessageProtocol,
  acceptedResponseProtocols: immutable.Seq[LagomMessageProtocol],
  principal:                 Option[Principal],
  headers:                   Map[String, immutable.Seq[(String, String)]]
) extends LagomMessageHeader

private[lagom] case class LagomResponseHeader(
  status:   Int,
  protocol: LagomMessageProtocol,
  headers:  Map[String, immutable.Seq[(String, String)]]
) extends LagomMessageHeader

private[lagom] trait LagomExceptionSerializer {
  def deserializeWebSocketException(code: Int, requestProtocol: LagomMessageProtocol, bytes: ByteString): Throwable
  def deserializeHttpException(code: Int, responseProtocol: LagomMessageProtocol, bytes: ByteString): Throwable
  def serialize(exception: Throwable, acceptedProtocols: immutable.Seq[LagomMessageProtocol]): LagomRawExceptionMessage
  def payloadTooLarge(message: String): Exception
  def policyViolation(message: String, detail: String): Exception
}

private[lagom] trait LagomRawExceptionMessage {
  def httpCode: Int
  def webSocketCode: Int
  def messageText: String
}
