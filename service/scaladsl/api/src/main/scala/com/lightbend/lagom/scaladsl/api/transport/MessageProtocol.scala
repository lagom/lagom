/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.transport

import java.nio.charset.Charset

import scala.io.Codec

/**
 * A message protocol.
 *
 * This describes the negotiated protocol being used for a message.  It has three elements, a content type, a charset,
 * and a version.
 *
 * The `contentType` may be registered mime type such as `application/json`, or it could be an application
 * specific content type, such as `application/vnd.myservice+json`.  It could also contain protocol versioning
 * information, such as `application/vnd.github.v3+json`.  During the protocol negotiation process, the
 * content type may be transformed, for example, if the content type contains a version, the configured
 * [[HeaderFilter]] will be expected to extract that version out into the `version`, leaving a `contentType` that
 * will be understood by the message serializer.
 *
 * The `charset` applies to text messages, if the message is not in a text format, then no `charset`
 * should be specified.  This is not only used in setting of content negotiation headers, it's also used as a hint to
 * the framework of when it can treat a message as text.  For example, if the charset is set, then when a message gets
 * sent via WebSockets, it will be sent as a text message, otherwise it will be sent as a binary message.
 *
 * The `version` is used to describe the version of the protocol being used. Lagom does not, out of the box,
 * prescribe any semantics around the version, from Lagom's perspective, two message protocols with different versions
 * are two different protocols. The version is however treated as a separate piece of information so that generic
 * parsers, such as json/xml, can make sensible use of the content type passed to them.  The version could come from
 * a media type header, but it does not necessarily have to come from there, it could come from the URI or any other
 * header.
 *
 * `MessageProtocol` instances can also be used in content negotiation, an empty value means that any value
 * is accepted.
 */
sealed trait MessageProtocol {
  val contentType: Option[String]
  val charset: Option[String]
  val version: Option[String]

  def withContentType(contentType: String): MessageProtocol
  def withCharset(charset: String): MessageProtocol
  def withVersion(version: String): MessageProtocol

  /**
   * Whether this message protocol is a text based protocol.
   *
   * This is determined by whether the charset is defined.
   *
   * @return true if this message protocol is text based.
   */
  def isText: Boolean = charset.isDefined || contentType.contains("application/json")

  /**
   * Whether the protocol uses UTF-8.
   *
   * @return true if the charset used by this protocol is UTF-8, false if it's some other encoding or if no charset is
   *         defined.
   */
  def isUtf8: Boolean = charset.exists(cs => Charset.forName(cs) == Codec.UTF8.charSet) || charset.isEmpty && contentType.contains("application/json")

  /**
   * Convert this message protocol to a content type header, if the content type is defined.
   *
   * @return The message protocol as a content type header.
   */
  def toContentTypeHeader: Option[String] = contentType.map(ct => charset.fold(ct)(cs => s"$ct; charset=$cs"))

}

object MessageProtocol {
  def fromContentTypeHeader(contentType: Option[String]): MessageProtocol = {
    contentType.fold(MessageProtocol.empty) { ct =>
      val parts = ct.split(";")
      val justContentType = parts(0)
      val charset = parts.collectFirst {
        case charsetPart if charsetPart.startsWith("charset=") => charsetPart.split("=", 2)(1)
      }
      MessageProtocol(Some(justContentType), charset, None)
    }
  }

  val empty: MessageProtocol = {
    MessageProtocolImpl(None, None, None)
  }

  def apply(contentType: Option[String] = None, charset: Option[String] = None, version: Option[String] = None): MessageProtocol = {
    MessageProtocolImpl(contentType, charset, version)
  }

  def unapply(messageProtocol: MessageProtocol): Option[(Option[String], Option[String], Option[String])] = {
    Some((messageProtocol.contentType, messageProtocol.charset, messageProtocol.version))
  }

  private case class MessageProtocolImpl(contentType: Option[String], charset: Option[String], version: Option[String]) extends MessageProtocol {
    override def withContentType(contentType: String): MessageProtocol = copy(contentType = Some(contentType))

    override def withCharset(charset: String): MessageProtocol = copy(charset = Some(charset))

    override def withVersion(version: String): MessageProtocol = copy(version = Some(version))
  }
}
