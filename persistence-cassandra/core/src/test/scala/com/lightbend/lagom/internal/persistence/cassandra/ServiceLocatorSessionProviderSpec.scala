/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.net.{ InetSocketAddress, URI }

import akka.actor.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ MustMatchers, WordSpec }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class ServiceLocatorSessionProviderSpec extends WordSpec with MustMatchers {

  val system = ActorSystem("test")
  val config: Config = ConfigFactory.load()
  val uri = new URI("http://localhost:8080")

  val locator = new ServiceLocatorAdapter {
    override def locateAll(name: String): Future[List[URI]] = {
      name match {
        case "existing" => Future.successful(List(uri))
        case "absent"   => Future.successful(Nil)
      }
    }

  }

  val providerConfig: Config = config.getConfig("lagom.persistence.read-side.cassandra")
  val provider = new ServiceLocatorSessionProvider(system, providerConfig)
  ServiceLocatorHolder(system).setServiceLocator(locator)

  "ServiceLocatorSessionProvider" should {

    "Get the address when the contact points exist" in {
      val future = provider.lookupContactPoints("existing")

      Await.result(future, 3.seconds) mustBe Seq(new InetSocketAddress(uri.getHost, uri.getPort))
    }

    "Fail the future when the contact points do not exist" in {
      val future = provider.lookupContactPoints("absent")

      intercept[NoContactPointsException] {
        Await.result(future, 3.seconds)
      }
    }
  }
}
