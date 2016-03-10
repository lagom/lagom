/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.io.File

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.javadsl.persistence.testkit.TestUtil

object PersistenceSpec {

  def config(testName: String): Config =
    TestUtil.persistenceConfig(testName, CassandraLauncher.randomPort, useServiceLocator = false)

  def getCallerName(clazz: Class[_]): String = {
    val s = (Thread.currentThread.getStackTrace map (_.getClassName) drop 1)
      .dropWhile(_ matches "(java.lang.Thread|.*PersistenceSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 ⇒ s
      case z  ⇒ s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

  def awaitPersistenceInit(system: ActorSystem): Unit =
    TestUtil.awaitPersistenceInit(system)

}

abstract class PersistenceSpec(system: ActorSystem) extends ActorSystemSpec(system) {

  def this(testName: String, config: Config) =
    this(ActorSystem(testName, config.withFallback(PersistenceSpec.config(testName))))

  def this(config: Config) = this(PersistenceSpec.getCallerName(getClass), config)

  def this() = this(ConfigFactory.empty())

  override def beforeAll {
    super.beforeAll()
    val cassandraDirectory = new File("target/" + system.name)
    CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, clean = true, port = 0)
    PersistenceSpec.awaitPersistenceInit(system)
  }

  override def afterAll {
    CassandraLauncher.stop()
    super.afterAll()
  }

}

