/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.URI
import java.util.{ Map => JMap }

import com.lightbend.lagom.gateway._
import play.api._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.guice.GuiceableModule.fromGuiceModule
import play.core.server.Server
import play.core.server.ServerConfig
import play.core.server.ServerProvider

import scala.util.control.NonFatal

class ServiceLocatorServer extends Closeable {
  private val logger: Logger = Logger(this.getClass())

  @volatile private var server: Server                    = _
  @volatile private var gatewayAddress: InetSocketAddress = _

  def start(
      serviceLocatorAddress: String,
      serviceLocatorPort: Int,
      serviceGatewayAddress: String,
      serviceGatewayHttpPort: Int,
      unmanagedServices: JMap[String, String],
      gatewayImpl: String
  ): Unit =
    synchronized {
      require(server == null, "Service locator is already running on " + server.mainAddress)

      // we create a single application which we will reuse for both gateway and locator
      val application = createApplication(
        ServiceGatewayConfig(
          serviceGatewayAddress,
          serviceGatewayHttpPort
        ),
        unmanagedServices
      )

      // the service locator is a play app
      Play.start(application)
      try {
        server = createServer(application, serviceLocatorAddress, serviceLocatorPort)
      } catch {
        case NonFatal(e) =>
          throw new RuntimeException(s"Unable to start service locator on port $serviceLocatorPort", e)
      }

      // the service gateway is a bare-AkkaHTTP server
      try {
        gatewayAddress = gatewayImpl match {
          case "netty"     => application.injector.instanceOf[NettyServiceGatewayFactory].start().address
          case "akka-http" => application.injector.instanceOf[AkkaHttpServiceGatewayFactory].start()
          case other       => sys.error("Unknown gateway implementation: " + other)
        }
      } catch {
        case NonFatal(e) =>
          throw new RuntimeException(s"Unable to start service gateway on port: $serviceGatewayHttpPort", e)
      }
      logger.info("Service locator can be reached at " + serviceLocatorAddress)
      logger.info("Service gateway can be reached at " + serviceGatewayAddress)
    }

  private def createApplication(
      serviceGatewayConfig: ServiceGatewayConfig,
      unmanagedServices: JMap[String, String]
  ): Application = {
    val initialSettings: Map[String, AnyRef] = Map(
      "ssl-config.loose.disableHostnameVerification" -> "true"
    )
    val environment = Environment.simple()
    new GuiceApplicationBuilder(environment, Configuration.load(environment, initialSettings))
      .overrides(new ServiceRegistryModule(serviceGatewayConfig, unmanagedServices))
      .build()
  }

  private def createServer(application: Application, host: String, port: Int): Server = {
    val config   = ServerConfig(address = host, port = Some(port), mode = Mode.Test)
    val provider = implicitly[ServerProvider]
    provider.createServer(config, application)
  }

  override def close(): Unit = synchronized {
    if (server == null) logger.debug("Service locator was already stopped")
    else {
      logger.debug("Stopping service locator...")
      server.stop()
      server = null
      logger.info("Service locator stopped")
    }
  }

  def serviceLocatorAddress: URI = {
    new URI(s"http://${server.mainAddress.getAddress.getHostAddress}:${server.mainAddress.getPort}")
  }

  def serviceGatewayAddress: URI = {
    // TODO: support multiple addresses for gateway (http vs https)
    new URI(s"http://${server.mainAddress.getAddress.getHostAddress}:${gatewayAddress.getPort}")
  }
}
