/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.discovery.impl

import java.util.Collections
import java.util.concurrent.ExecutionException

import akka.actor.{ ActorSystem, Props }
import com.lightbend.lagom.discovery.ServiceRegistryActor
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.transport.NotFound

import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import com.lightbend.lagom.discovery.UnmanagedServices
import akka.NotUsed
import java.util.concurrent.TimeUnit
import java.net.URI

import com.lightbend.lagom.internal.javadsl.registry.{ RegisteredService, ServiceRegistry, ServiceRegistryService }

class ServiceRegistryImplSpec extends WordSpecLike with Matchers {

  private val testTimeoutInSeconds = 5

  "A service registry" should {
    "allow to register a service" in withServiceRegistry() { registry =>
      val expectedUrl = new URI("http://localhost:9000")
      val serviceName = "fooservice"
      registry.register(serviceName).invoke(new ServiceRegistryService(expectedUrl, Collections.emptyList[ServiceAcl]))
      val registeredUrl = registry.lookup(serviceName).invoke(NotUsed).toCompletableFuture().get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      assertResult(expectedUrl)(registeredUrl)
    }

    "allow to register a service of same service twice (idempotent)" in withServiceRegistry() { registry =>
      val expectedUrl = new URI("http://localhost:9000")
      val serviceName = "fooservice"
      registry.register(serviceName).invoke(new ServiceRegistryService(expectedUrl, Collections.emptyList[ServiceAcl]))
        .toCompletableFuture().get(testTimeoutInSeconds, TimeUnit.SECONDS)
      registry.register(serviceName).invoke(new ServiceRegistryService(expectedUrl, Collections.emptyList[ServiceAcl]))
        .toCompletableFuture().get(testTimeoutInSeconds, TimeUnit.SECONDS)
      val registeredUrl = registry.lookup(serviceName).invoke(NotUsed).toCompletableFuture().get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      assertResult(expectedUrl)(registeredUrl)
    }

    "throw NotFound for services that aren't registered" in withServiceRegistry() { registry =>
      val ee = the[ExecutionException] thrownBy registry.lookup("fooservice").invoke(NotUsed).toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      ee.getCause shouldBe a[NotFound]
    }

    "disallow registering the different endpoint for same name twice or more" in withServiceRegistry() { registry =>
      val url1 = new URI("http://localhost:9000")
      val url2 = new URI("http://localhost:9001")
      val serviceName = "fooservice"
      registry.register(serviceName).invoke(new ServiceRegistryService(url1, Collections.emptyList[ServiceAcl]))
        .toCompletableFuture.get(testTimeoutInSeconds, TimeUnit.SECONDS)
      intercept[ExecutionException] {
        registry.register(serviceName).invoke(new ServiceRegistryService(url2, Collections.emptyList[ServiceAcl]))
          .toCompletableFuture.get(testTimeoutInSeconds, TimeUnit.SECONDS)
      }
    }

    "allow to retrieve the full list of registered services" in {
      val url = new URI("http://localhost:9000")
      val name = "fooservice"
      val service = new ServiceRegistryService(url, Collections.emptyList[ServiceAcl])
      val registeredService = Map(name -> service)
      val expectedRegisteredServices: List[RegisteredService] = (for {
        (name, service) <- registeredService
      } yield RegisteredService.of(name, service.uri))(collection.breakOut)

      // SUT
      withServiceRegistry(registeredService) { registry =>
        val registered = registry.registeredServices().invoke().toCompletableFuture().get(testTimeoutInSeconds, TimeUnit.SECONDS)

        import scala.collection.JavaConverters._
        assertResult(expectedRegisteredServices)(registered.asScala.toList)
      }
    }

    def withServiceRegistry[T](registeredServices: Map[String, ServiceRegistryService] = Map.empty)(body: ServiceRegistry => T): T = {
      val system = ActorSystem()
      try {
        val actor = system.actorOf(Props(new ServiceRegistryActor(new UnmanagedServices(registeredServices))))
        val registry = new ServiceRegistryImpl(actor)
        body(registry)
      } finally {
        system.terminate()
      }
    }
  }
}
