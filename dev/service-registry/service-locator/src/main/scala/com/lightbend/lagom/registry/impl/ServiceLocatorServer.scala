/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.io.Closeable
import java.net.{ InetSocketAddress, URI }
import java.util.{ Map => JMap }

import com.lightbend.lagom.gateway._
import play.api.{ Application, Logger, Mode, Play }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.guice.GuiceableModule.fromGuiceModule
import play.core.server.{ ReloadableServer, ServerConfig, ServerProvider }

import scala.util.control.NonFatal

class ServiceLocatorServer extends Closeable {
  private val logger: Logger = Logger(this.getClass())

  @volatile private var server: ReloadableServer = _
  @volatile private var gatewayAddress: InetSocketAddress = _

  def start(serviceLocatorAddress: String, serviceLocatorPort: Int, serviceGatewayAddress: String, serviceGatewayPort: Int, unmanagedServices: JMap[String, String], gatewayImpl: String): Unit = synchronized {
    require(server == null, "Service locator is already running on " + server.mainAddress)

    val application = createApplication(ServiceGatewayConfig(serviceGatewayAddress, serviceGatewayPort), unmanagedServices)
    Play.start(application)
    try {
      server = createServer(application, serviceLocatorAddress, serviceLocatorPort)
    } catch {
      case NonFatal(e) =>
        throw new RuntimeException(s"Unable to start service locator on port $serviceLocatorPort", e)
    }
    try {
      gatewayAddress = gatewayImpl match {
        case "netty"     => application.injector.instanceOf[NettyServiceGatewayFactory].start().address
        case "akka-http" => application.injector.instanceOf[AkkaHttpServiceGatewayFactory].start()
        case other       => sys.error("Unknown gateway implementation: " + other)
      }
    } catch {
      case NonFatal(e) =>
        throw new RuntimeException(s"Unable to start service gateway on port $serviceGatewayPort", e)
    }
    logger.info("Service locator can be reached at " + serviceLocatorAddress)
    logger.info("Service gateway can be reached at " + serviceGatewayAddress)
  }

  private def createApplication(serviceGatewayConfig: ServiceGatewayConfig, unmanagedServices: JMap[String, String]): Application = {
    new GuiceApplicationBuilder()
      .overrides(new ServiceRegistryModule(serviceGatewayConfig, unmanagedServices))
      .build()
  }

  private def createServer(application: Application, host: String, port: Int): ReloadableServer = {
    val config = ServerConfig(address = host, port = Some(port), mode = Mode.Test)
    val provider = implicitly[ServerProvider]
    provider.createServer(config, application)
  }

  override def close(): Unit = synchronized {
    if (server == null) Logger.logger.debug("Service locator was already stopped")
    else {
      logger.debug("Stopping service locator...")
      server.stop()
      server = null
      logger.info("Service locator stopped")
    }
  }

  def serviceLocatorAddress: URI = {
    new URI(s"http://${server.mainAddress.getHostName}:${server.mainAddress.getPort}")
  }

  def serviceGatewayAddress: URI = {
    new URI(s"http://${gatewayAddress.getHostName}:${gatewayAddress.getPort}")
  }
}
