/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client

import java.net.URI
import java.util.concurrent.TimeUnit

import com.lightbend.lagom.internal.client.CircuitBreakers
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }
import play.Configuration

import scala.compat.java8.OptionConverters._
import scala.concurrent.Future

class ConfigurationServiceLocatorSpec extends WordSpec with Matchers {

  val serviceLocator = new ConfigurationServiceLocator(new Configuration(ConfigFactory.parseString(
    """
      |lagom.services {
      |  foo = "http://localhost:10001"
      |  bar = "http://localhost:10002"
      |}
    """.stripMargin
  )), new CircuitBreakers(null, null, null))

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
