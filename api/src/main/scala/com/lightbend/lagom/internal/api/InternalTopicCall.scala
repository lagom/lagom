/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import scala.collection.JavaConverters._

import org.pcollections.HashTreePMap
import org.pcollections.PMap

import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.Descriptor.TopicHolder
import com.lightbend.lagom.javadsl.api.broker.Publisher
import com.lightbend.lagom.javadsl.api.broker.Subscriber
import com.lightbend.lagom.javadsl.api.broker.Topic
import com.lightbend.lagom.javadsl.api.broker.Topic.TopicId
import com.lightbend.lagom.javadsl.api.broker.modules.Properties
import com.lightbend.lagom.javadsl.api.broker.modules.Properties.Property
import com.lightbend.lagom.javadsl.api.deser.MessageSerializer

import akka.stream.javadsl.{ Source => JSource }
import akka.util.ByteString

class InternalTopicCall[Message] private (
  override val topicId:           TopicId,
  val topicHolder:                TopicHolder,
  override val messageSerializer: MessageSerializer[Message, ByteString],
  _properties:                    PMap[Property[AnyRef], AnyRef]
) extends TopicCall[Message] {

  def this(topicId: TopicId, topicHolder: TopicHolder, messageSerializer: MessageSerializer[Message, ByteString]) =
    this(topicId, topicHolder, messageSerializer, HashTreePMap.empty());

  def withTopicHolder(topicHolder: TopicHolder): InternalTopicCall[Message] =
    new InternalTopicCall(topicId, topicHolder, messageSerializer, _properties);

  override def withMessageSerializer(messageSerializer: MessageSerializer[Message, ByteString]): InternalTopicCall[Message] =
    new InternalTopicCall(topicId, topicHolder, messageSerializer, _properties)

  override def withProperty[T](property: Property[T], value: T): InternalTopicCall[Message] =
    new InternalTopicCall(topicId, topicHolder, messageSerializer, _properties.plus(property.asInstanceOf[Property[AnyRef]], value.asInstanceOf[AnyRef]))

  override def properties(): Properties = new Properties(_properties)

  override def toString(): String = {
    val propertiesAsText = _properties.asScala.map(entry => s"(${entry._1} -> ${entry._2})").mkString(", ")
    s"TopicCall{topicId=${topicId.value}, topicHolder=$topicHolder, messageSerializer=$messageSerializer, properties=[" + propertiesAsText + "] }"
  }
}

final class TopicFeed[Message](val topicId: TopicId, val messages: JSource[Message, _]) extends Topic[Message] {

  def this(messages: JSource[Message, _]) = this(TopicId.of("__unresolved__"), messages)

  def withTopicId(topicId: TopicId): Topic[Message] = new TopicFeed(topicId, messages)

  override def subscribe(): Subscriber[Message] =
    throw new UnsupportedOperationException("Topic#subscribe is not permitted in the service's topic implementation.");

  override def publisher(): Publisher[Message] =
    throw new UnsupportedOperationException("Topic#publisher is not permitted in the service's topic implementation.");
}