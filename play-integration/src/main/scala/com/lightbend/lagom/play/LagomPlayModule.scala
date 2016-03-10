/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.play

import java.util.Optional
import javax.inject.{ Singleton, Inject }

import com.google.inject.Provider
import com.lightbend.lagom.internal.registry.{ ServiceRegistryModule, ServiceRegistryService, ServiceRegistry }
import akka.NotUsed
import com.lightbend.lagom.javadsl.api.{ ServiceInfo, ServiceAcl }
import com.lightbend.lagom.javadsl.api.transport.Method
import play.api.{ Mode, Configuration, Environment }
import play.api.inject.{ ApplicationLifecycle, Binding, Module }
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._

class LagomPlayModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {

    val serviceRegistryStart = if (ServiceRegistryModule.isServiceRegistryServiceLocatorEnabled(configuration) &&
      environment.mode == Mode.Dev) {
      Seq(
        bind[PlayRegisterWithServiceRegistry].toSelf.eagerly()
      )
    } else {
      Nil
    }

    Seq(
      bind[ServiceInfo].toProvider[PlayServiceInfoProvider]
    ) ++ serviceRegistryStart
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
  private val serviceUrl = s"http://$httpAddress:$httpPort"

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
