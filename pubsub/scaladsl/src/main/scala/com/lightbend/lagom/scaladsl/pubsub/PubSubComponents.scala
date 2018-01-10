/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.pubsub

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.scaladsl.PubSubRegistryImpl

trait PubSubComponents {
  def actorSystem: ActorSystem

  lazy val pubSubRegistry: PubSubRegistry = new PubSubRegistryImpl(actorSystem)
}
