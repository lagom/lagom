/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.util.Collections
import java.util.Optional
import java.util.concurrent.ExecutionException

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.transport.Method
import com.lightbend.lagom.javadsl.api.transport.NotFound
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import akka.NotUsed
import java.util.concurrent.TimeUnit
import java.net.URI

import akka.util.Timeout
import com.lightbend.lagom.internal.javadsl.registry.RegisteredService
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService

import scala.concurrent.Await
import scala.concurrent.duration._

class ServiceRegistryImplSpec extends WordSpecLike with Matchers {

  private val testTimeoutInSeconds = 5
  private implicit val testTimeout = Timeout(testTimeoutInSeconds.seconds)

  "A service registry" should {
    "allow to register a service" in withServiceRegistry() { registry =>
      val expectedUrl = new URI("http://localhost:9000")
      val serviceName = "fooservice"
      registry.register(serviceName).invoke(ServiceRegistryService.of(expectedUrl, Collections.emptyList[ServiceAcl]))
      val registeredUrl = registry
        .lookup(serviceName, Optional.empty())
        .invoke(NotUsed)
        .toCompletableFuture()
        .get(
          testTimeoutInSeconds,
          TimeUnit.SECONDS
        )
      assertResult(expectedUrl)(registeredUrl)
    }

    "allow to register a service of same service twice (idempotent)" in withServiceRegistry() { registry =>
      val expectedUrl = new URI("http://localhost:9000")
      val serviceName = "fooservice"
      registry
        .register(serviceName)
        .invoke(ServiceRegistryService.of(expectedUrl, Collections.emptyList[ServiceAcl]))
        .toCompletableFuture()
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)
      registry
        .register(serviceName)
        .invoke(ServiceRegistryService.of(expectedUrl, Collections.emptyList[ServiceAcl]))
        .toCompletableFuture()
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)
      val registeredUrl = registry
        .lookup(serviceName, Optional.empty())
        .invoke(NotUsed)
        .toCompletableFuture()
        .get(
          testTimeoutInSeconds,
          TimeUnit.SECONDS
        )
      assertResult(expectedUrl)(registeredUrl)
    }

    "throw NotFound for services that aren't registered" in withServiceRegistry() { registry =>
      val ee = the[ExecutionException] thrownBy registry
        .lookup("fooservice", Optional.empty())
        .invoke(NotUsed)
        .toCompletableFuture
        .get(
          testTimeoutInSeconds,
          TimeUnit.SECONDS
        )
      ee.getCause shouldBe a[NotFound]
    }

    "disallow registering the different endpoint for same name twice or more" in withServiceRegistry() { registry =>
      val url1        = new URI("http://localhost:9000")
      val url2        = new URI("http://localhost:9001")
      val serviceName = "fooservice"
      registry
        .register(serviceName)
        .invoke(ServiceRegistryService.of(url1, Collections.emptyList[ServiceAcl]))
        .toCompletableFuture
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)
      intercept[ExecutionException] {
        registry
          .register(serviceName)
          .invoke(ServiceRegistryService.of(url2, Collections.emptyList[ServiceAcl]))
          .toCompletableFuture
          .get(testTimeoutInSeconds, TimeUnit.SECONDS)
      }
    }

    "allow to retrieve the full list of registered services" in {
      val url               = new URI("http://localhost:9000")
      val name              = "fooservice"
      val service           = ServiceRegistryService.of(url, Collections.emptyList[ServiceAcl])
      val registeredService = Map(name -> service)
      val expectedRegisteredServices: List[RegisteredService] = List(
        RegisteredService.of(name, service.uris().get(0), Optional.empty()),
        RegisteredService.of(name, service.uris().get(0), Optional.of("http"))
      )

      // SUT
      withServiceRegistry(registeredService) { registry =>
        val registered =
          registry.registeredServices().invoke().toCompletableFuture().get(testTimeoutInSeconds, TimeUnit.SECONDS)

        //        List(RegisteredService{name=fooservice, url=http://localhost:9000})
        //        List(RegisteredService{name=fooservice, url=http://localhost:9000}, RegisteredService{name=fooservice, url=http://localhost:9000})

        import scala.collection.JavaConverters._
        assertResult(expectedRegisteredServices)(registered.asScala.toList)
      }
    }

    "can locate 'http' services with or without port-names" in withServiceRegistryActor() { actor =>
      val registry    = new ServiceRegistryImpl(actor)
      val serviceName = "fooservice"
      val expectedUrl = URI.create("http://localhost")
      registry
        .register(serviceName)
        .invoke(
          ServiceRegistryService.of(
            expectedUrl,
            Collections.singletonList(new ServiceAcl(Optional.of(Method.GET), Optional.of("/")))
          )
        )
        .toCompletableFuture
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)

      withClue("lookup without a portName") {
        val registeredUrl = registry
          .lookup(serviceName, Optional.empty())
          .invoke(NotUsed)
          .toCompletableFuture
          .get(
            testTimeoutInSeconds,
            TimeUnit.SECONDS
          )
        assertResult(expectedUrl)(registeredUrl)
      }

      withClue("lookup with portName 'http'") {
        val registeredUrl = registry
          .lookup(serviceName, Optional.of("http"))
          .invoke(NotUsed)
          .toCompletableFuture
          .get(
            testTimeoutInSeconds,
            TimeUnit.SECONDS
          )
        assertResult(expectedUrl)(registeredUrl)
      }
    }

    "can locate 'https' services only when passing port-name" in withServiceRegistryActor() { actor =>
      val registry    = new ServiceRegistryImpl(actor)
      val serviceName = "fooservice"
      val expectedUrl = URI.create("https://localhost")
      registry
        .register(serviceName)
        .invoke(
          ServiceRegistryService.of(
            expectedUrl,
            Collections.singletonList(new ServiceAcl(Optional.of(Method.GET), Optional.of("/")))
          )
        )
        .toCompletableFuture
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)

      withClue("lookup without a portName") {
        val ee = the[ExecutionException] thrownBy registry
          .lookup(serviceName, Optional.empty())
          .invoke(NotUsed)
          .toCompletableFuture
          .get(
            testTimeoutInSeconds,
            TimeUnit.SECONDS
          )
        ee.getCause shouldBe a[NotFound]
      }

      withClue("lookup with portName 'https'") {
        val registeredUrl = registry
          .lookup(serviceName, Optional.of("https"))
          .invoke(NotUsed)
          .toCompletableFuture
          .get(
            testTimeoutInSeconds,
            TimeUnit.SECONDS
          )
        assertResult(expectedUrl)(registeredUrl)
      }
    }

    "default to well-known port for http URLs if no port number provided" ignore withServiceRegistryActor() { actor =>
      val registry = new ServiceRegistryImpl(actor)
      registry
        .register("fooservice")
        .invoke(
          ServiceRegistryService.of(
            URI.create("http://localhost"),
            Collections.singletonList(new ServiceAcl(Optional.of(Method.GET), Optional.of("/")))
          )
        )
        .toCompletableFuture
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)

      Await.result(actor ? ServiceRegistryActor.Route("GET", "/", None), testTimeoutInSeconds.seconds) match {
        case ServiceRegistryActor.Found(address) =>
          address.getHost should ===("localhost")
          address.getPort should ===(80)
      }
    }

    "default to well-known port for https URLs if no port number provided" ignore withServiceRegistryActor() { actor =>
      val registry = new ServiceRegistryImpl(actor)
      registry
        .register("fooservice")
        .invoke(
          ServiceRegistryService.of(
            URI.create("https://localhost"),
            Collections.singletonList(new ServiceAcl(Optional.of(Method.GET), Optional.of("/")))
          )
        )
        .toCompletableFuture
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)

      Await.result(actor ? ServiceRegistryActor.Route("GET", "/", None), testTimeoutInSeconds.seconds) match {
        case ServiceRegistryActor.Found(address) =>
          address.getHost should ===("localhost")
          address.getPort should ===(443)
      }
    }

    "be able to register URLs that have no port and no ACLs" in withServiceRegistry() { registry =>
      registry
        .register("fooservice")
        .invoke(ServiceRegistryService.of(URI.create("tcp://localhost"), Collections.emptyList[ServiceAcl]))
        .toCompletableFuture
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)

      val registeredUrl = registry
        .lookup("fooservice", Optional.empty())
        .invoke(NotUsed)
        .toCompletableFuture
        .get(testTimeoutInSeconds, TimeUnit.SECONDS)
      registeredUrl should ===(URI.create("tcp://localhost"))
    }

    def withServiceRegistry[T](
        registeredServices: Map[String, ServiceRegistryService] = Map.empty
    )(body: ServiceRegistry => T): T = {
      withServiceRegistryActor(registeredServices) { actor =>
        body(new ServiceRegistryImpl(actor));
      }
    }

    def withServiceRegistryActor[T](
        registeredServices: Map[String, ServiceRegistryService] = Map.empty
    )(body: ActorRef => T): T = {
      val system = ActorSystem()
      try {
        val actor = system.actorOf(Props(new ServiceRegistryActor(new UnmanagedServices(registeredServices))))
        body(actor)
      } finally {
        system.terminate()
      }
    }
  }
}
