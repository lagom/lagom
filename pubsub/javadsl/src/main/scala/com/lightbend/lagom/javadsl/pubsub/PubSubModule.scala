/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.pubsub

import com.lightbend.lagom.internal.javadsl.pubsub.PubSubRegistryImpl
import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

/**
 * Guice module for the PubSub API.
 */
class PubSubModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    // eagerSingleton because the distributed registry benefits from being alive as early as possible.
    bind[PubSubRegistry].to[PubSubRegistryImpl].eagerly()
  )

}
