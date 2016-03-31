/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.play

import java.net.URI
import java.util.Optional

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import com.google.inject.Provider
import com.lightbend.lagom.internal.registry.ServiceRegistry
import com.lightbend.lagom.internal.registry.ServiceRegistryService
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.transport.Method

import akka.NotUsed
import javax.inject.Inject
import javax.inject.Singleton
import play.api.Configuration
import play.api.Environment
import play.api.inject.ApplicationLifecycle
import play.api.inject.Binding
import play.api.inject.Module

class LagomPlayModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[PlayRegisterWithServiceRegistry].toSelf.eagerly(),
      bind[ServiceInfo].toProvider[PlayServiceInfoProvider]
    )
  }
}

@Singleton
class PlayServiceInfoProvider @Inject() (config: Configuration) extends Provider[ServiceInfo] {
  lazy val get = {
    val serviceName = config.underlying.getString("lagom.play.service-name")
    new ServiceInfo(serviceName)
  }
}

class PlayRegisterWithServiceRegistry @Inject() (config: Configuration, serviceInfo: ServiceInfo, serviceRegistry: ServiceRegistry, applicationLifecycle: ApplicationLifecycle) {

  private val httpAddress = config.underlying.getString("play.server.http.address")
  private val httpPort = config.underlying.getString("play.server.http.port")
  private val serviceUrl = new URI(s"http://$httpAddress:$httpPort")

  private val acls = config.underlying.getConfigList("lagom.play.acls").asScala.map { aclConfig =>
    val method = if (aclConfig.hasPath("method")) {
      Optional.of(new Method(aclConfig.getString("method")))
    } else Optional.empty[Method]
    val pathRegex = if (aclConfig.hasPath("path-regex")) {
      Optional.of(aclConfig.getString("path-regex"))
    } else Optional.empty[String]

    new ServiceAcl(method, pathRegex)
  }
  private val service = new ServiceRegistryService(serviceUrl, acls.asJava)
  serviceRegistry.register().invoke(serviceInfo.serviceName, service)

  applicationLifecycle.addStopHook(() => serviceRegistry.unregister().invoke(serviceInfo.serviceName, NotUsed.getInstance()).toScala)
}
