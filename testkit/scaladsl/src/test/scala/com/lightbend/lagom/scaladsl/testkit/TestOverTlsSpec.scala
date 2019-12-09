/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.typesafe.config.ConfigFactory
import javax.net.ssl.SSLContext
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import play.api.libs.ws.ahc.AhcWSClientConfigFactory
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 *
 */
class TestOverTlsSpec extends AnyWordSpec with Matchers with ScalaFutures {
  val timeout      = PatienceConfiguration.Timeout(Span(5, Seconds))
  val defaultSetup = ServiceTest.defaultSetup.withCluster(false)

  "TestOverTls" when {
    "started with ssl" should {
      "not provide an ssl port by default" in {
        ServiceTest.withServer(defaultSetup)(new TestTlsApplication(_)) { server =>
          server.playServer.httpsPort shouldBe empty
        }
      }

      "provide an ssl port" in {
        ServiceTest.withServer(defaultSetup.withSsl())(new TestTlsApplication(_)) { server =>
          server.playServer.httpsPort should not be empty
        }
      }

      "provide an ssl context for the client" in {
        ServiceTest.withServer(defaultSetup.withSsl())(new TestTlsApplication(_)) { server =>
          server.clientSslContext should not be empty
        }
      }

      "complete an RPC call" in {
        ServiceTest.withServer(defaultSetup.withSsl())(new TestTlsApplication(_)) { server =>
          // The client's provided by Lagom's ServiceTest default to use HTTP
          val client: TestTlsService = server.serviceClient.implement[TestTlsService]
          val response               = client.sampleCall().invoke()
          whenReady(response, timeout) { r =>
            r should be("sample response")
          }
        }
      }

      // #tls-test-service
      "complete a WS call over HTTPS" in {
        val setup = defaultSetup.withSsl()
        ServiceTest.withServer(setup)(new TestTlsApplication(_)) { server =>
          implicit val actorSystem = server.application.actorSystem
          implicit val ctx         = server.application.executionContext
          // To explicitly use HTTPS on a test you must create a client of your
          // own and make sure it uses the provided SSLContext
          val wsClient = buildCustomWS(server.clientSslContext.get)
          // use `localhost` as authority
          val url = s"https://localhost:${server.playServer.httpsPort.get}/api/sample"
          val response =
            wsClient
              .url(url)
              .get()
              .map {
                _.body[String]
              }
          whenReady(response, timeout) { r =>
            r should be("sample response")
          }
        }
      }
      // #tls-test-service
    }
  }

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

trait TestTlsService extends Service {
  import Service._

  def sampleCall(): ServiceCall[NotUsed, String]

  final override def descriptor: Descriptor =
    named("test-tls")
      .withCalls(
        restCall(Method.GET, "/api/sample", sampleCall _)
      )
}

class TestTlsServiceImpl() extends TestTlsService {
  override def sampleCall: ServiceCall[NotUsed, String] = ServiceCall { _ =>
    Future.successful("sample response")
  }
}

class TestTlsApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with LocalServiceLocator
    with AhcWSComponents {
  override lazy val lagomServer: LagomServer = serverFor[TestTlsService](new TestTlsServiceImpl())
}
