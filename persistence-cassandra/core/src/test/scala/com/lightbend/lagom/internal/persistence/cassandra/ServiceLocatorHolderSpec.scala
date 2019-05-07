/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.actor.ActorSystem
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class ServiceLocatorHolderSpec extends WordSpec with MustMatchers {
  val system = ActorSystem("test")

  "ServiceLocatorHolder" should {
    "timeout when no service locator is found" in {
      val eventually = ServiceLocatorHolder(system).serviceLocatorEventually
      assertThrows[NoServiceLocatorException](
        Await.result(eventually, ServiceLocatorHolder.TIMEOUT + 2.seconds)
      )
    }
  }
}
