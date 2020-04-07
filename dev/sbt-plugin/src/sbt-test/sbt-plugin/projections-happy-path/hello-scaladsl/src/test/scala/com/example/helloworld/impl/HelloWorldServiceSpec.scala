/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.helloworld.impl

import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import com.example.helloworld.api._
import org.scalatest.concurrent.Eventually

import scala.concurrent.Await
import scala.concurrent.duration._

class HelloWorldServiceSpec
    extends AsyncWordSpec
    with Matchers
    with BeforeAndAfterAll
    with Eventually {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup
      .withCassandra()
  ) { ctx =>
    new HelloWorldApplication(ctx) with LocalServiceLocator
  }

  val client: HelloWorldService =
    server.serviceClient.implement[HelloWorldService]

  override protected def afterAll(): Unit = server.stop()

  "Hello World service" should {

    "say hello" in {
      client.hello("Alice").invoke().map { answer =>
        answer should ===("""Hello, Alice!
            |Started reports: default-projected-message
            |Stopped reports: default-projected-message
            |""".stripMargin)
      }
    }

    "allow responding with a custom message" in {
      for {
        _ <- client.useGreeting("Bob", "Hi").invoke()
        answer <- client.hello("Bob").invoke()
      } yield {
        answer should ===("""Hi, Bob!
              |Started reports: default-projected-message
              |Stopped reports: default-projected-message
              |""".stripMargin)
      }

      implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 25.seconds, interval = 300.millis)
      eventually{
        val answer = Await.result(client.hello("Bob").invoke(), 5.seconds)
        answer should ===(
          """Hi, Bob!
            |Started reports: Hi
            |Stopped reports: default-projected-message
            |""".stripMargin
        )
      }

    }
  }
}
