/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.play

import java.net.URI
import java.util.{ List => JList }
import java.util.Optional
import javax.inject.Inject

import com.lightbend.lagom.internal.javadsl.registry.{ ServiceRegistry, ServiceRegistryService }
import com.lightbend.lagom.javadsl.api.{ ServiceAcl, ServiceInfo }
import com.lightbend.lagom.javadsl.api.transport.Method
import com.typesafe.config.Config
import play.api.inject.{ ApplicationLifecycle, Binding, Module }
import play.api.{ Configuration, Environment, Logger }

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.util.{ Success, Try }

class LagomPlayModule extends Module {
  private val logger = Logger(this.getClass)

  override def bindings(environment: Environment, config: Configuration): Seq[Binding[_]] = {
    val maybeServiceInfoBinding: Option[Binding[ServiceInfo]] = prepareServiceInfoBinding(config.underlying)
    val playRegistry = bind[PlayRegisterWithServiceRegistry].toSelf.eagerly()

    Seq(
      playRegistry
    ) ++ maybeServiceInfoBinding.toList

  }

  private def prepareServiceInfoBinding(config: Config) = {
    val triedServiceName = Try(config.getString("lagom.play.service-name"))
    val triedAcls: Try[JList[_ <: Config]] = Try(config.getConfigList("lagom.play.acls"))

    val warning = "Service setup via 'application.conf' is deprecated. Remove 'lagom.play.service-name' and/or " +
      "'lagom.play.acls' and use 'bindServiceInfo' on your Guice's Module class."

    val maybeServiceInfoBinding = (triedServiceName, triedAcls) match {
      case (Success(serviceName), Success(aclList)) => {
        logger.warn(warning)
        // create a ServiceInfo in case user doesn't see the warning
        val acls = parseAclList(aclList)
        Some(bind[ServiceInfo].toInstance(ServiceInfo.of(serviceName, acls: _*)))
      }
      case (Success(serviceName), _) => {
        logger.warn(warning)
        // create a ServiceInfo in case user doesn't see the warning
        Some(bind[ServiceInfo].toInstance(ServiceInfo.of(serviceName)))
      }
      case (_, Success(_)) => {
        logger.warn(warning)
        // can't create a ServiceInfo because service-name is missing
        None
      }
      case _ => None
    }
    maybeServiceInfoBinding
  }

  private def parseAclList(aclList: JList[_ <: Config]): Seq[ServiceAcl] = {
    aclList.asScala.map { aclConfig =>
      val method = if (aclConfig.hasPath("method")) {
        Optional.of(new Method(aclConfig.getString("method")))
      } else Optional.empty[Method]
      val pathRegex = if (aclConfig.hasPath("path-regex")) {
        Optional.of(aclConfig.getString("path-regex"))
      } else Optional.empty[String]
      new ServiceAcl(method, pathRegex)
    }
  }
}

class PlayRegisterWithServiceRegistry @Inject() (config: Config, serviceInfo: ServiceInfo, serviceRegistry: ServiceRegistry, applicationLifecycle: ApplicationLifecycle) {

  @deprecated(message = "prefer constructor using typesafe Config instead", since = "1.4.0")
  def this(config: Configuration, serviceInfo: ServiceInfo, serviceRegistry: ServiceRegistry,
           applicationLifecycle: ApplicationLifecycle) =
    this(config.underlying, serviceInfo, serviceRegistry, applicationLifecycle)

  private val httpAddress = config.getString("play.server.http.address")
  private val httpPort = config.getString("play.server.http.port")
  private val serviceUrl = new URI(s"http://$httpAddress:$httpPort")

  // TODO: ServiceRegistryService should not flatmap the ACL lists (locatableService's names are lost)
  private val serviceAcls = serviceInfo.getAcls
  private val service = new ServiceRegistryService(serviceUrl, serviceAcls)
  // TODO: fix -> this register operation is registering all ACLs under the microservice name, not under each locatable service name. Will lead to unlocatable.
  serviceRegistry.register(serviceInfo.serviceName()).invoke(service)

  applicationLifecycle.addStopHook(() => serviceRegistry.unregister(serviceInfo.serviceName()).invoke().toScala)
}
