/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.testkit

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig
import com.lightbend.lagom.internal.persistence.testkit.{ AwaitPersistenceInit => InternalAwaitPersistenceInit }
import com.typesafe.config.Config

@deprecated("Internal class, not intended for direct use.", "1.5.0")
private class AwaitPersistenceInit extends InternalAwaitPersistenceInit

@deprecated("Internal interface, not intended for direct use.", "1.5.0")
trait AbstractTestUtil {

  def clusterConfig(): Config = PersistenceTestConfig.ClusterConfig

  def awaitPersistenceInit(system: ActorSystem): Unit = InternalAwaitPersistenceInit.awaitPersistenceInit(system)

}
