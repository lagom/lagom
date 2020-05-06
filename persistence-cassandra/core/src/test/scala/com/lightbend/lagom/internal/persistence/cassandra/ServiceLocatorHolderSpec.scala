/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.cassandra

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ServiceLocatorHolderSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  val system = ActorSystem("test")

  protected override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(actorSystem = system, verifySystemShutdown = true)
  }

  "ServiceLocatorHolder" should {
    "timeout when no service locator is found" in {
      val eventually = ServiceLocatorHolder(system).serviceLocatorEventually
      assertThrows[NoServiceLocatorException](
        Await.result(eventually, ServiceLocatorHolder.TIMEOUT + 2.seconds)
      )
    }
  }
}
