/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.cluster

import scala.concurrent.duration._

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.lightbend.lagom.internal.cluster.JoinClusterImpl

class JoinClusterModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[JoinCluster]).asEagerSingleton()
  }
}

private[lagom] class JoinCluster @Inject() (system: ActorSystem) {
  JoinClusterImpl.join(system)
}
