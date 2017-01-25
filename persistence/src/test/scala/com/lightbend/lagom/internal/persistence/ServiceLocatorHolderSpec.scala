/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import java.net.URI
import java.util.concurrent.CompletionStage
import java.util.function.Function

import com.lightbend.lagom.javadsl.api.Descriptor.Call
import com.lightbend.lagom.javadsl.api.ServiceLocator
import org.scalatest.{ MustMatchers, WordSpec }

import scala.util.Success

class ServiceLocatorHolderSpec extends WordSpec with MustMatchers {
  private val locator = new ServiceLocator {
    override def doWithService[T](name: String, serviceCall: Call[_, _], block: Function[URI, CompletionStage[T]]) = ???
    override def locate(name: String, serviceCall: Call[_, _]) = ???
  }

  "ServiceLocatorHolder" should {
    "supply the locator when the consumer asks for it before it's ready" in {
      val holder = new ServiceLocatorHolder()
      val future = holder.serviceLocatorEventually
      holder.serviceLocator mustBe None

      holder.setServiceLocator(locator)

      future.isCompleted mustBe true
      future.value mustBe Some(Success(locator))
      holder.serviceLocator mustBe Some(locator)
    }

    "supply the locator when the consumer asks for it after it's been set" in {
      val holder = new ServiceLocatorHolder()
      holder.setServiceLocator(locator)

      val future = holder.serviceLocatorEventually
      future.isCompleted mustBe true
      future.value mustBe Some(Success(locator))
      holder.serviceLocator mustBe Some(locator)
    }

    "throw an exception if the holder is set twice" in {
      val holder = new ServiceLocatorHolder()
      holder.setServiceLocator(locator)

      intercept[IllegalArgumentException] {
        holder.setServiceLocator(locator)
      }
    }
  }
}
