/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api

import java.net.URI
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }

import scala.compat.java8.OptionConverters._

class ConfigurationServiceLocatorSpec extends WordSpec with Matchers {

  val serviceLocator = new ConfigurationServiceLocator(ConfigFactory.parseString(
    """
      |lagom.services {
      |  foo = "http://localhost:10001"
      |  bar = "http://localhost:10002"
      |}
    """.stripMargin
  ))

  def locate(serviceName: String) =
    serviceLocator.locate(serviceName).toCompletableFuture.get(10, TimeUnit.SECONDS).asScala

  "ConfigurationServiceLocator" should {
    "return a found service" in {
      locate("foo") should contain(URI.create("http://localhost:10001"))
      locate("bar") should contain(URI.create("http://localhost:10002"))
    }
    "return none for not found service" in {
      locate("none") shouldBe None
    }
  }

}
