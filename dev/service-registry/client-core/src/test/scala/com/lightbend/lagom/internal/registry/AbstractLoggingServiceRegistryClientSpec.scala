/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.registry

import java.net.URI

import org.scalatest.AsyncWordSpec
import org.scalatest.Matchers

import scala.concurrent.Future

class AbstractLoggingServiceRegistryClientSpec extends AsyncWordSpec with Matchers {

  private val client = new AbstractLoggingServiceRegistryClient {
    override def internalLocateAll(serviceName: String, portName: Option[String]): Future[List[URI]] =
      serviceName match {
        case "failing-service"    => Future.failed(new IllegalArgumentException("Ignore: expected error"))
        case "empty-service"      => Future.successful(List())
        case "successful-service" => Future.successful(List(URI.create("http://localhost:8080")))
      }
  }

  "AbstractLoggingServiceRegistryClient" when {
    /*
     * This class is very simple, and the tests are straightforward, but it's
     * still useful to run the tests to manually inspect log messages.
     */

    "internal lookup fails" in {
      client
        .locateAll("failing-service", None)
        .failed
        .map(_ shouldBe an[IllegalArgumentException])
    }

    "internal lookup has no result" in {
      client
        .locateAll("empty-service", None)
        .map(_ shouldEqual Nil)
    }

    "internal lookup has a successful result" in {
      client
        .locateAll("successful-service", None)
        .map(_ shouldEqual List(URI.create("http://localhost:8080")))
    }

  }
}
