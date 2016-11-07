/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.api.broker

import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.broker.Topic
import play.api.inject.Injector

import scala.util.control.NonFatal

/**
 * Factory for creating topics.
 *
 * Note: This class is useful only to create new message broker module implementations,
 * and should not leak into the user api.
 */
trait TopicFactory {
  def create[Message](topicCall: TopicCall[Message]): Topic[Message]
}

/**
 * Provider for a topic factory.
 *
 * This layer of indirection is provided so that the ServiceClientImplementor doesn't have to directly depend on a
 * TopicFactory, it can be optional.
 */
trait TopicFactoryProvider {
  def get: Option[TopicFactory]
}

@Singleton
class InjectorTopicFactoryProvider @Inject() (injector: Injector) extends TopicFactoryProvider {
  override lazy val get: Option[TopicFactory] = try {
    Some(injector.instanceOf[TopicFactory])
  } catch {
    case NonFatal(e) => None
  }
}

object NoTopicFactoryProvider extends TopicFactoryProvider {
  override val get = None
}