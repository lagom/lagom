/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.cassandra

import akka.persistence.cassandra.testkit.{ CassandraLauncher => AkkaCassandraLauncher }

/**
 * This launcher basically does what the Akka testkit cassandra launcher does, but in addition, adds a shutdown hook
 * so that Cassandra gets shutdown cleanly, since Cassandra will lose data if not shutdown cleanly.
 */
object CassandraLauncher extends App {
  AkkaCassandraLauncher.main(args)

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      AkkaCassandraLauncher.stop()
    }
  })
}
