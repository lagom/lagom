package com.lightbend.lagom.internal.client

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, FunSuite, Matchers, OptionValues}

class ServiceSchemeConfigTest extends FunSuite with Matchers with OptionValues {

  val config =
    """
      | lagom.akka.discovery {
      |
      |  schemes = [
      |    { service = "cas_native", scheme = "tcp" },
      |    { service = "kafka_native",  scheme = "tcp" },
      |    { service = "hello-grpc", scheme = "http" }
      |  ]
      |
      |  defaultScheme = "http"
      |
      | }
    """.stripMargin

  test("Should load ServiceLocatorConfig") {
    val serviceLocatorConfig = ServiceSchemeConfig.load(ConfigFactory.parseString(config))

    serviceLocatorConfig.defaultScheme shouldBe "http"

    val casNative = serviceLocatorConfig.lookUp("cas_native").value
    casNative.scheme shouldBe "tcp"

    val helloGrpc = serviceLocatorConfig.lookUp("hello-grpc").value
    helloGrpc.scheme shouldBe "http"

  }
}
