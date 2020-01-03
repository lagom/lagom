/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.testkit

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig
import com.lightbend.lagom.internal.persistence.testkit.{ AwaitPersistenceInit => InternalAwaitPersistenceInit }
import com.typesafe.config.Config

@deprecated("Internal object, not intended for direct use.", "1.5.0")
object AbstractTestUtil {
  class AwaitPersistenceInit extends InternalAwaitPersistenceInit
}

@deprecated("Internal trait, not intended for direct use.", "1.5.0")
trait AbstractTestUtil {

  def clusterConfig(): Config = PersistenceTestConfig.ClusterConfig

  def awaitPersistenceInit(system: ActorSystem): Unit = InternalAwaitPersistenceInit.awaitPersistenceInit(system)

}
