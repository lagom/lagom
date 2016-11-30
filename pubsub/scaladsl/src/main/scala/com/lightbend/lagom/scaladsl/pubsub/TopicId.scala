/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.pubsub

import scala.reflect.ClassTag

/**
 * Identifier of a topic. The messages of this topic will be instances of the
 * `messageClass` or subclasses thereof. The `qualifier` can be used to
 * distinguish topics that are using the same `messageClass`. In other words
 * the identifier of the topic is the combination of the `messageClass` and
 * the `qualifier`.
 *
 * @tparam T the type of the messages that can be published to this topic
 */
sealed trait TopicId[T] {
  val messageClass: Class[T]
  val qualifier: Option[String]
  final val name: String = {
    qualifier match {
      case Some(q) => s"${messageClass.getName}-$q"
      case None    => messageClass.getName
    }
  }
}

object TopicId {
  def apply[T: ClassTag]: TopicId[T] = TopicIdImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], None)
  def apply[T: ClassTag](qualifier: String): TopicId[T] =
    TopicIdImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], Some(qualifier))

  private case class TopicIdImpl[T](messageClass: Class[T], qualifier: Option[String]) extends TopicId[T]
}
