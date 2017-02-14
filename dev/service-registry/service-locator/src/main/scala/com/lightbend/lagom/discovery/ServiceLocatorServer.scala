/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.discovery

import java.io.Closeable
import java.net.URI
import java.util.{ Map => JMap }

import com.lightbend.lagom.discovery.impl.ServiceRegistryModule
import com.lightbend.lagom.gateway.{ ServiceGateway, ServiceGatewayConfig, ServiceGatewayFactory }
import play.api.Application
import play.api.Logger
import play.api.Mode
import play.api.Play
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.guice.GuiceableModule.fromGuiceModule
import play.core.server.ServerConfig
import play.core.server.ServerProvider
import play.core.server.ServerWithStop

import scala.util.control.NonFatal

class ServiceLocatorServer extends Closeable {
  private val logger: Logger = Logger(this.getClass())

  @volatile private var server: ServerWithStop = _
  @volatile private var gateway: ServiceGateway = _

  def start(serviceLocatorPort: Int, serviceGatewayPort: Int, unmanagedServices: JMap[String, String]): Unit = synchronized {
    require(server == null, "Service locator is already running on " + server.mainAddress)

    val application = createApplication(ServiceGatewayConfig(serviceGatewayPort), unmanagedServices)
    Play.start(application)
    try {
      server = createServer(application, serviceLocatorPort)
    } catch {
      case NonFatal(e) =>
        throw new RuntimeException(s"Unable to start service locator on port $serviceLocatorPort", e)
    }
    try {
      gateway = application.injector.instanceOf[ServiceGatewayFactory].start()
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

  private def createServer(application: Application, port: Int): ServerWithStop = {
    val config = ServerConfig(port = Some(port), mode = Mode.Test)
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
    // Converting InetSocketAddress into URL is not that simple. 
    // Because we know the service locator is running locally, I'm hardcoding the hostname and protocol. 
    new URI(s"http://localhost:${server.mainAddress.getPort}")
  }

  def serviceGatewayAddress: URI = {
    new URI(s"http://localhost:${gateway.address.getPort}")
  }
}
