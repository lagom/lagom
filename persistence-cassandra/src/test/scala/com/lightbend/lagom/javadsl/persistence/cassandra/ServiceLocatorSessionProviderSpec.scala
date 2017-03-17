/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.net.{ InetSocketAddress, URI }
import java.util.Optional
import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.function.Function

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.persistence.ServiceLocatorHolder
import com.lightbend.lagom.internal.persistence.cassandra.{ NoContactPointsException, ServiceLocatorSessionProvider }
import com.lightbend.lagom.javadsl.api.Descriptor.Call
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ MustMatchers, WordSpec }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceLocatorSessionProviderSpec extends WordSpec with MustMatchers {
  val system = ActorSystem("test")
  val config: Config = ConfigFactory.load()
  val uri = new URI("http://localhost:8080")

  val locator = new ServiceLocator {
    override def locate(name: String, serviceCall: Call[_, _]): CompletionStage[Optional[URI]] = {
      name match {
        case "existing" => CompletableFuture.completedFuture(Optional.of(uri))
        case "absent"   => CompletableFuture.completedFuture(Optional.empty())
      }
    }

    override def doWithService[T](name: String, serviceCall: Call[_, _], block: Function[URI, CompletionStage[T]]): CompletionStage[Optional[T]] = ???
  }

  val providerConfig: Config = config.getConfig("lagom.persistence.read-side.cassandra")
  val provider = new ServiceLocatorSessionProvider(system, providerConfig)
  ServiceLocatorHolder(system).setServiceLocator(locator)

  "ServiceLocatorSessionProvider" should {
    "Get the address when the contact points exist" in {
      val future = provider.lookupContactPoints("existing")

      Await.result(future, 3 seconds) mustBe Seq(new InetSocketAddress(uri.getHost, uri.getPort))
    }

    "Fail the future when the contact points do not exist" in {
      val future = provider.lookupContactPoints("absent")

      intercept[NoContactPointsException] {
        Await.result(future, 3 seconds)
      }
    }
  }
}
