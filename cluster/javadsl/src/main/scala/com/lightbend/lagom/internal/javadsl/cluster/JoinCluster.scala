/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.cluster

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.lightbend.lagom.internal.cluster.JoinClusterImpl
import play.api.Environment

class JoinClusterModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[JoinCluster]).asEagerSingleton()
  }
}

private[lagom] class JoinCluster @Inject()(system: ActorSystem, environment: Environment) {
  JoinClusterImpl.join(system, environment)
}
