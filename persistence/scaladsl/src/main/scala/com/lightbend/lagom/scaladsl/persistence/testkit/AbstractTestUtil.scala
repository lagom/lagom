/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.testkit

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.testkit.{ AwaitPersistenceInit => InternalAwaitPersistenceInit }
import com.typesafe.config.{ Config, ConfigFactory }

@deprecated("Internal object, not intended for direct use.", "1.5.0")
object AbstractTestUtil {
  class AwaitPersistenceInit extends InternalAwaitPersistenceInit
}

@deprecated("Internal trait, not intended for direct use.", "1.5.0")
trait AbstractTestUtil {

  def clusterConfig(): Config = ConfigFactory.parseString(s"""
    akka.actor.provider = akka.cluster.ClusterActorRefProvider
    akka.remote.netty.tcp.port = 0
    akka.remote.netty.tcp.hostname = 127.0.0.1
  """)

  def awaitPersistenceInit(system: ActorSystem): Unit = InternalAwaitPersistenceInit.awaitPersistenceInit(system)

}
