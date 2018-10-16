package com.lightbend.lagom.internal.client

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, FunSuite, Matchers, OptionValues}

class ServiceLocatorConfigTest extends FunSuite with Matchers with OptionValues {

  val config =
    """
      | lagom.akka.discovery {
      |
      |  portNames = [
      |    { service = "cas_native", portName = null, scheme = "tcp" },
      |    { service = "kafka_native", portName = null, scheme = "tcp" },
      |    { service = "hello-grpc", portName = "grpc", scheme = "http" }
      |  ]
      |
      |  defaultPortName = "http"
      |  defaultScheme = "http"
      |
      | }
    """.stripMargin

  test("Should load ServiceLocatorConfig") {
    val serviceLocatorConfig = ServiceLocatorConfig.load(ConfigFactory.parseString(config))

    serviceLocatorConfig.defaultPortName shouldBe "http"
    serviceLocatorConfig.defaultScheme shouldBe "http"

    val casNative = serviceLocatorConfig.lookUp("cas_native").value
    casNative.portName shouldBe empty
    casNative.scheme.value shouldBe "tcp"

//    val helloGrpc = serviceLocatorConfig.lookUp("hello-grpc").value
//    helloGrpc.portName.value shouldBe "grpc"
//    helloGrpc.scheme.value shouldBe "http"

  }
}
