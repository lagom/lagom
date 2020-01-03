/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.pubsub

import com.lightbend.lagom.internal.javadsl.pubsub.PubSubRegistryImpl
import play.api.inject._

/**
 * Guice module for the PubSub API.
 */
class PubSubModule
    extends SimpleModule(
      // eagerSingleton because the distributed registry benefits from being alive as early as possible.
      bind[PubSubRegistry].to[PubSubRegistryImpl].eagerly()
    )
