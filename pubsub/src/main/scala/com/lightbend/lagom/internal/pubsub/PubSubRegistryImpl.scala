/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.pubsub

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import com.typesafe.config.Config
import com.lightbend.lagom.javadsl.pubsub.PubSubRef
import com.lightbend.lagom.javadsl.pubsub.PubSubRegistry
import com.lightbend.lagom.javadsl.pubsub.TopicId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
private[lagom] class PubSubRegistryImpl(system: ActorSystem, conf: Config) extends PubSubRegistry {
  import akka.cluster.pubsub.DistributedPubSubMediator._

  @Inject
  def this(system: ActorSystem) =
    this(system, system.settings.config.getConfig("lagom.pubsub"))

  private val pubsub = DistributedPubSub(system)
  private val bufferSize: Int = conf.getInt("subscriber-buffer-size")

  override def refFor[T](topic: TopicId[T]): PubSubRef[T] =
    new PubSubRef(topic, pubsub.mediator, system, bufferSize)

}
