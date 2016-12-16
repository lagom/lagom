/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.play

import java.net.URI
import javax.inject.Inject

import com.lightbend.lagom.internal.javadsl.registry.{ ServiceRegistry, ServiceRegistryService }
import com.lightbend.lagom.javadsl.api.ServiceInfo
import play.api.inject.{ ApplicationLifecycle, Binding, Module }
import play.api.{ Configuration, Environment }

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._

class LagomPlayModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[PlayRegisterWithServiceRegistry].toSelf.eagerly()
    )
  }
}

class PlayRegisterWithServiceRegistry @Inject() (config: Configuration, serviceInfo: ServiceInfo, serviceRegistry: ServiceRegistry, applicationLifecycle: ApplicationLifecycle) {
  private val httpAddress = config.underlying.getString("play.server.http.address")
  private val httpPort = config.underlying.getString("play.server.http.port")
  private val serviceUrl = new URI(s"http://$httpAddress:$httpPort")

  //  TODO: ServiceRegistryService should not flatmap the ACL lists (locatableService's names are lost)
  private val serviceAcls = serviceInfo.getLocatableServices.values().asScala.flatMap(_.asScala).toSeq.asJava
  private val service = new ServiceRegistryService(serviceUrl, serviceAcls)
  // TODO: fix -> this register operation is registering all ACLs under the microservice name, not under each locatable service name. Will lead to unlocatable.
  serviceRegistry.register(serviceInfo.serviceName()).invoke(service)

  applicationLifecycle.addStopHook(() => serviceRegistry.unregister(serviceInfo.serviceName()).invoke().toScala)
}
