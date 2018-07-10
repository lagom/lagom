/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.testkit

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.testkit.{ AwaitPersistenceInit => InternalAwaitPersistenceInit }
import com.typesafe.config.{ Config, ConfigFactory }

@deprecated("Internal class, not intended for direct use.", "1.5.0")
private class AwaitPersistenceInit extends InternalAwaitPersistenceInit

@deprecated("Internal interface, not intended for direct use.", "1.5.0")
trait AbstractTestUtil {

  def clusterConfig(): Config = ConfigFactory.parseString(s"""
    akka.actor.provider = akka.cluster.ClusterActorRefProvider
    akka.remote.netty.tcp.port = 0
    akka.remote.netty.tcp.hostname = 127.0.0.1
    """)

  def awaitPersistenceInit(system: ActorSystem): Unit = InternalAwaitPersistenceInit.awaitPersistenceInit(system)

}
