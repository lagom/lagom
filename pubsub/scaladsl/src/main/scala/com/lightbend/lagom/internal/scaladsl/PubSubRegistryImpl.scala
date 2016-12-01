/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import com.lightbend.lagom.scaladsl.pubsub.{ PubSubRef, PubSubRegistry, TopicId }
import com.typesafe.config.Config

private[lagom] class PubSubRegistryImpl(system: ActorSystem, conf: Config) extends PubSubRegistry {

  def this(system: ActorSystem) =
    this(system, system.settings.config.getConfig("lagom.pubsub"))

  private val pubsub = DistributedPubSub(system)
  private val bufferSize: Int = conf.getInt("subscriber-buffer-size")

  override def refFor[T](topic: TopicId[T]): PubSubRef[T] =
    new PubSubRef(topic, pubsub.mediator, system, bufferSize)

}
