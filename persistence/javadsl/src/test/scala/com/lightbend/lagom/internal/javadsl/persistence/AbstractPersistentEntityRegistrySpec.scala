/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.google.inject.Guice
import com.lightbend.lagom.javadsl.cluster.testkit.ActorSystemModule
import com.lightbend.lagom.javadsl.persistence.{ NamedEntity, TestEntity }
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }

class AbstractPersistentEntityRegistrySpec
  extends FlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  @volatile var system: ActorSystem = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.parseString(
      "akka.actor.provider = akka.cluster.ClusterActorRefProvider \n" +
        "akka.remote.netty.tcp.port = 0 \n" +
        "akka.remote.netty.tcp.hostname = 127.0.0.1 \n" +
        "akka.loglevel = INFO \n"
    )
    system = ActorSystem.create("PubSubTest", config)
    Cluster(system).join(Cluster(system).selfAddress)
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  def withRegistry[T](block: (AbstractPersistentEntityRegistry) => T): T = {
    val injector = Guice.createInjector(new ActorSystemModule(system))
    val registry = new AbstractPersistentEntityRegistry(system, injector) {
      override protected val journalId = "testing-journal"
    }
    block(registry)
  }

  val uselessId = "123"

  // ------------------------------------------------------------

  behavior of "AbstractPersistentEntityRegistry"

  it should "register and refFor given a class type" in withRegistry { registry =>
    val entityClass = classOf[TestEntity]
    registry.register(entityClass)
    registry.refFor(entityClass, uselessId)
  }

  it should "register and refFor given a class type for a Persistent Entity with overriden name" in withRegistry { registry =>
    val entityClass = classOf[NamedEntity]
    registry.register(entityClass)
    registry.refFor(entityClass, uselessId)
  }

  it should "throw IAE if refFor  before registering" in withRegistry { registry =>
    val entityClass = classOf[TestEntity]
    assertThrows[IllegalArgumentException] {
      registry.refFor(entityClass, uselessId)
    }
  }

}
