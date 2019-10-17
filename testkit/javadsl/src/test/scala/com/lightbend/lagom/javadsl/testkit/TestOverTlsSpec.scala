/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.AbstractModule
import com.lightbend.lagom.javadsl.api.ServiceCall
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup
import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer
import com.lightbend.lagom.javadsl.testkit.services.tls.TestTlsService
import com.typesafe.config.ConfigFactory
import javax.inject.Inject
import javax.net.ssl.SSLContext
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.api.libs.ws.ahc.AhcWSClientConfigFactory
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.inject.guice.GuiceApplicationBuilder

import scala.compat.java8.FunctionConverters._

/**
 *
 */
class TestOverTlsSpec extends WordSpec with Matchers with ScalaFutures {

  val timeout      = PatienceConfiguration.Timeout(Span(5, Seconds))
  val defaultSetup = ServiceTest.defaultSetup.withCluster(false)

  "TestOverTls" when {
    "started with ssl" should {

      "not provide an ssl port by default" in {
        withServer(defaultSetup) { server =>
          server.portSsl.isPresent shouldBe false
        }
      }

      "provide an ssl port" in {
        withServer(defaultSetup.withSsl()) { server =>
          server.portSsl.isPresent shouldBe true
        }
      }

      "provide an ssl context for the client" in {
        withServer(defaultSetup.withSsl()) { server =>
          server.clientSslContext.isPresent shouldBe true
        }
      }

      "complete an RPC call" in {
        withServer(defaultSetup.withSsl()) { server =>
          // The client's provided by Lagom's ServiceTest default to use HTTP
          val client: TestTlsService = server.client(classOf[TestTlsService])
          val response = client
            .sampleCall()
            .invoke()
            .toCompletableFuture
            .get(5, TimeUnit.SECONDS)
          response should be("sample response")
        }
      }

      "complete a WS call over HTTPS" in {
        withServer(defaultSetup.withSsl()) { server =>
          implicit val actorSystem = server.app.asScala().actorSystem
          implicit val ctx         = actorSystem.dispatcher
          // To explicitly use HTTPS on a test you must create a client of your own and make sure it uses
          // the provided SSLContext
          val wsClient = buildCustomWS(server.clientSslContext.get)
          val response =
            wsClient
              .url(s"https://localhost:${server.portSsl.get}/api/sample")
              .get()
              .map {
                _.body[String]
              }
          whenReady(response, timeout) { r =>
            r should be("sample response")
          }
        }
      }
    }
  }

  def withServer(setup: Setup)(block: TestServer => Unit): Unit = {
    ServiceTest.withServer(setup.configureBuilder((registerService _).asJava), block(_))
  }

  def registerService(builder: GuiceApplicationBuilder): GuiceApplicationBuilder =
    builder.bindings(new TestTlsServiceModule)

  private def buildCustomWS(sslContext: SSLContext)(implicit actorSystem: ActorSystem) = {
    implicit val mat = ActorMaterializer()
    // This setting enables the use of `SSLContext.setDefault` on the following line.
    val sslConfig = ConfigFactory.parseString("play.ws.ssl.default = true").withFallback(ConfigFactory.load())
    SSLContext.setDefault(sslContext)
    val config = AhcWSClientConfigFactory.forConfig(sslConfig)
    // This wsClient will use the `SSLContext` from `SSLContext.getDefault` (instead of the internal config-based)
    val wsClient = StandaloneAhcWSClient(config)
    wsClient
  }
}

// Thhe actual TestTlsService for javadsl tests is written in java. See TestTlsService.java
//trait TestTlsService extends Service

class TestTlsServiceImpl @Inject() () extends TestTlsService {
  override def sampleCall(): ServiceCall[NotUsed, String] = {
    new ServiceCall[NotUsed, String]() {
      override def invoke(request: NotUsed): CompletionStage[String] =
        CompletableFuture.completedFuture("sample response")
    }
  }
}

class TestTlsServiceModule extends AbstractModule with ServiceGuiceSupport {
  override def configure(): Unit = bindService(classOf[TestTlsService], classOf[TestTlsServiceImpl])
}
