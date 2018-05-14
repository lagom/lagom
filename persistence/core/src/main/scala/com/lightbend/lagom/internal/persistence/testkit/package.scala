/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import com.typesafe.config.{ Config, ConfigFactory }
import play.api.{ Configuration, Environment }

package object testkit {
  private[lagom] lazy val clusterConfig: Config = ConfigFactory.parseString(
    s"""
    akka.actor.provider = akka.cluster.ClusterActorRefProvider
    akka.remote.netty.tcp.port = 0
    akka.remote.netty.tcp.hostname = 127.0.0.1
    """
  )

  private[lagom] def loadTestConfig(): Config = Configuration.load(Environment.simple()).underlying
}
