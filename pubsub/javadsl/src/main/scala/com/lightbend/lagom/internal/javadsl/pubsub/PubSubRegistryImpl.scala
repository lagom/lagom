/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.pubsub

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import com.typesafe.config.Config
import javax.inject.Inject
import javax.inject.Singleton

import com.lightbend.lagom.javadsl.pubsub.{ PubSubRef, PubSubRegistry, TopicId }

@Singleton
private[lagom] class PubSubRegistryImpl(system: ActorSystem, conf: Config) extends PubSubRegistry {

  @Inject
  def this(system: ActorSystem) =
    this(system, system.settings.config.getConfig("lagom.pubsub"))

  private val pubsub = DistributedPubSub(system)
  private val bufferSize: Int = conf.getInt("subscriber-buffer-size")

  override def refFor[T](topic: TopicId[T]): PubSubRef[T] =
    new PubSubRef(topic, pubsub.mediator, system, bufferSize)

}
