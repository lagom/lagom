/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.lightbend.lagom.scaladsl.persistence.NamedEntity
import com.lightbend.lagom.scaladsl.persistence.TestEntity
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FlatSpec
import org.scalatest.Matchers

class AbstractPersistentEntityRegistrySpec extends FlatSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

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
    val registry = new AbstractPersistentEntityRegistry(system)
    block(registry)
  }

  val uselessId = "123"

  // ------------------------------------------------------------

  behavior.of("AbstractPersistentEntityRegistry")

  it should "register and refFor given a class type" in withRegistry { registry =>
    registry.register(new TestEntity(system))
    registry.refFor[TestEntity](uselessId)
  }

  it should "register and refFor given a class type for a Persistent Entity with overridden name" in withRegistry {
    registry =>
      registry.register(new NamedEntity())
      registry.refFor[NamedEntity](uselessId)
  }

  it should "throw IAE if refFor  before registering" in withRegistry { registry =>
    assertThrows[IllegalArgumentException] {
      registry.refFor[TestEntity](uselessId)
    }
  }

}
