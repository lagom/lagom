/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode.internal.registry

import java.net.InetAddress
import java.net.URI

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery.Resolved
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures._

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class LagomDevModeServiceDiscoverySpec
    extends TestKit(ActorSystem("LagomDevModeSimpleServiceDiscoverySpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  private val client = new StaticServiceRegistryClient(
    Map(
      "test-service"              -> List(URI.create("http://localhost:8080")),
      "test-service-without-port" -> List(URI.create("http://localhost"))
    )
  )

  protected override def afterAll(): Unit = {
    shutdown(verifySystemShutdown = true)
  }

  private val discovery = LagomDevModeServiceDiscovery(system)
  discovery.setServiceRegistryClient(client)

  "DevModeSimpleServiceDiscoverySpec" should {
    "resolve services in the registry" in {
      val expected =
        Resolved("test-service", List(ResolvedTarget("localhost", Some(8080), Some(InetAddress.getLocalHost))))
      discovery.lookup("test-service", 100.milliseconds).futureValue shouldBe expected
    }

    "allow missing ports" in {
      val expected =
        Resolved("test-service-without-port", List(ResolvedTarget("localhost", None, Some(InetAddress.getLocalHost))))
      discovery.lookup("test-service-without-port", 100.milliseconds).futureValue shouldBe expected
    }
  }
}

private class StaticServiceRegistryClient(registrations: Map[String, List[URI]]) extends ServiceRegistryClient {
  override def locateAll(serviceName: String, portName: Option[String]): Future[immutable.Seq[URI]] =
    Future.successful(registrations.getOrElse(serviceName, Nil))
}
