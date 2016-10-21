/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.util.Locale

import com.lightbend.lagom.internal.api.transport.{ LagomMessageProtocol, LagomRequestHeader, LagomResponseHeader }
import com.lightbend.lagom.javadsl.api.transport.{ MessageProtocol, RequestHeader, ResponseHeader }
import org.pcollections.{ HashTreePMap, PSequence, TreePVector }

import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._
import scala.collection.immutable

object LagomTransportConverters {
  def convertLagomMessageProtocol(lagomMessageProtocol: LagomMessageProtocol): MessageProtocol =
    new MessageProtocol(lagomMessageProtocol.contentType.asJava, lagomMessageProtocol.charset.asJava, lagomMessageProtocol.version.asJava)

  def convertMessageProtocol(messageProtocol: MessageProtocol): LagomMessageProtocol = {
    new LagomMessageProtocol(messageProtocol.contentType().asScala, messageProtocol.charset().asScala, messageProtocol.version().asScala)
  }

  def convertRequestHeader(requestHeader: RequestHeader): LagomRequestHeader = {
    new LagomRequestHeader(requestHeader.method().name(), requestHeader.uri(),
      convertMessageProtocol(requestHeader.protocol()),
      requestHeader.acceptedResponseProtocols().asScala.map(convertMessageProtocol).to[immutable.Seq],
      requestHeader.principal().asScala,
      requestHeader.headers().asScala.toSeq.flatMap {
        case (key, values) => values.asScala.map(key -> _)
      }.groupBy(_._1.toLowerCase(Locale.ENGLISH)).map {
        case (key, values) => key -> values.to[immutable.Seq]
      })
  }

  def convertLagomResponseHeader(lagomResponseHeader: LagomResponseHeader): ResponseHeader = {
    new ResponseHeader(lagomResponseHeader.status, convertLagomMessageProtocol(lagomResponseHeader.protocol),
      HashTreePMap.from(lagomResponseHeader.headers.map {
        case (key, keyValue :: keyValues) =>
          keyValue._1 -> TreePVector.singleton(keyValue._2).plusAll(keyValues.map(_._2).asJava)
        case (key, Nil) => key -> (TreePVector.empty(): PSequence[String])
      }.asJava))
  }
}
