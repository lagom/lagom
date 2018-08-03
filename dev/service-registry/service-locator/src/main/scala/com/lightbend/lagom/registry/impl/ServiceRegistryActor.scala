/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.net.URI
import java.util.regex.Pattern

import akka.Done
import akka.actor.{ Actor, Status }
import com.lightbend.lagom.internal.javadsl.registry.{ RegisteredService, ServiceRegistryService }
import com.lightbend.lagom.javadsl.api.transport.{ TransportErrorCode, TransportException }
import javax.inject.Inject
import org.pcollections.{ PSequence, TreePVector }
import play.api.Logger

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

object ServiceRegistryActor {
  case class Lookup(serviceName: String, portName: Option[String])
  case class Remove(name: String)
  case class Register(name: String, service: ServiceRegistryService)
  case class Route(method: String, path: String, portName: Option[String])
  case object GetRegisteredServices
  case class RegisteredServices(services: PSequence[RegisteredService])
  sealed trait RouteResult
  case class Found(address: URI) extends RouteResult
  case class NotFound(registry: Map[String, (URI, ServiceRegistryService)]) extends RouteResult

}

case class ServiceName(name: String) extends AnyVal

// identifies a single entry on the registry. This Registry will expand portname's `Some(tcp)`
// and `Some(`http`)` duplicating the entry on the registry to also use `None` for backwards compatibility
case class ServiceRegistryKey(serviceName: ServiceName, portName: Option[String])

object InternalRegistry {

  def build(unmanagedServices: UnmanagedServices): InternalRegistry = new InternalRegistry(
    unmanagedServices.services.flatMap {
      case (serviceName, serviceRegistryService) =>
        serviceRegistryService.uris().asScala.flatMap {
          buildRegistryItem(serviceName, serviceRegistryService)
        }
    }.toMap
  )

  private def buildRegistryItem(serviceName: String, serviceRegistryService: ServiceRegistryService)(serviceUri: URI): Seq[(ServiceRegistryKey, (URI, ServiceRegistryService))] = {
    val portNames = {
      serviceUri.getScheme match {
        case "tcp" =>
          Seq(None) // using "tcp://" defaults to having no portName
        case "http" =>
          // using "http://" defaults to having no portName and also Some("http") so "http" becomes the
          // default result when searching without a `portName` query.
          Seq(None, Some(serviceUri.getScheme))
        case _ => Seq(Some(serviceUri.getScheme))
      }
    }
    val registryItems: Seq[(ServiceRegistryKey, (URI, ServiceRegistryService))] = portNames.map { pn =>
      val srk = ServiceRegistryKey(ServiceName(serviceName), pn)
      srk -> (serviceUri, serviceRegistryService)
    }
    registryItems
  }

  def build(serviceName: String, details: ServiceRegistryService): Map[ServiceRegistryKey, (URI, ServiceRegistryService)] =
    details.uris().asScala.flatMap {
      buildRegistryItem(serviceName, details)
    }.toMap

}

/**
 * @param reg map using servicename and portname as keys and a single URI as value. The original ServiceRegistryService
 *            where eack K/V in the map was extracted from is added into the value to be able to traceback.
 */
class InternalRegistry(var reg: Map[ServiceRegistryKey, (URI, ServiceRegistryService)]) {
  private val logger: Logger = Logger(classOf[ServiceLocatorServer])

  def list(): Seq[(ServiceName, Option[String], URI)] = reg.toSeq.map { case (k, v) => (k.serviceName, k.portName, v._1) }

  /** Simple view of the registry that removes the portName info, grouping registries per ServiceName */
  val serviceValues: Map[ServiceName, (URI, ServiceRegistryService)] = reg.map { case (k, v) => k.serviceName -> v }

  def lookup(serviceName: String, portName: Option[String]): Option[URI] = reg.get(ServiceRegistryKey(ServiceName(serviceName), portName)).map(_._1)

  def register(serviceName: String, details: ServiceRegistryService): Any = {
    val subset: Map[ServiceRegistryKey, (URI, ServiceRegistryService)] = reg.filter { case (k, _) => k.serviceName.name == serviceName }
    if (subset.isEmpty) {
      if (logger.isDebugEnabled) {
        logger.debug(s"Registering service [$serviceName] with ACLs [${details.acls().asScala.map { acl => acl.toString }.mkString(", ")}] on ${details.uris().asScala.mkString(",")}).")
      }
      reg = reg ++ InternalRegistry.build(serviceName, details)
      Done
    } else {
      val actualDetails: ServiceRegistryService = subset.values.map(_._2).head
      if (actualDetails.equals(details)) {
        Done // idempotent, same already registered
      } else {
        Status.Failure(new ServiceAlreadyRegistered(serviceName))
      }
    }
  }

  def remove(serviName: String) =
    reg = reg.filterNot { case (k, _) => k.serviceName.name == serviName }
}

class InternalRouter {
  private val logger: Logger = Logger(classOf[InternalRouter])
  import ServiceRegistryActor._

  private var router = PartialFunction.empty[Route, (URI, ServiceRegistryService)]
  // maps a Route to ServiceNames
  private var routerFunctions = Seq.empty[PartialFunction[Route, (URI, ServiceRegistryService)]]
  private var simpleRegistry: Map[String, (URI, ServiceRegistryService)] = Map.empty[String, (URI, ServiceRegistryService)]

  def routeFor(route: ServiceRegistryActor.Route): RouteResult = {
    router.lift(route).fold[RouteResult](NotFound(simpleRegistry)) {
      case (uri, _) =>
        Found(uri)
    }
  }

  def rebuild(registry: InternalRegistry): Unit = {
    routerFunctions = registry.reg.map { case (k, v) => (k.serviceName.name, k.portName, v._1, v._2) }.toSeq.flatMap { case (name, portname, uri, details) => serviceRouter(name, portname, uri, details) }
    simpleRegistry = registry.serviceValues.map { case (k, v) => k.name -> v }
    router = routerFunctions.foldLeft(PartialFunction.empty[Route, (URI, ServiceRegistryService)])(_ orElse _)
  }

  private def serviceRouter(serviceName: String, registeredPortName: Option[String], uri: URI, service: ServiceRegistryService): Seq[PartialFunction[Route, (URI, ServiceRegistryService)]] = {
    // lazy because if there's no ACLs, then there's no need to create an InetSocketAddress, and hence no need to fail
    // if the port can't be calculated.
    val routerFunctions: Seq[PartialFunction[Route, (URI, ServiceRegistryService)]] = service.acls.asScala.map {
      case acl =>
        acl.method().asScala -> acl.pathRegex().asScala.map(Pattern.compile)
    }.map {
      case (aclMethod, pathRegex) =>
        val pf: PartialFunction[Route, (URI, ServiceRegistryService)] = {
          case Route(method, path, requestedPortName) if aclMethod.forall(_.name == method) && pathRegex.forall(_.matcher(path).matches()) && registeredPortName == requestedPortName =>
            (uri, service)
        }
        pf
    }
    routerFunctions
  }

  def warnOnAmbiguity(route: Route): Unit = {
    if (logger.isWarnEnabled) {
      val servingRoutes = routerFunctions.filter(_.isDefinedAt(route))
      if (servingRoutes.size > 1) {
        val servers = servingRoutes.map(_.apply(route))
        logger.warn(s"Ambiguous route resolution serving route: $route. Route served by ${servers.head} but also matches ${servers.tail.mkString("[", ",", "]")}.")
      }
    }
  }

}

class ServiceRegistryActor @Inject() (unmanagedServices: UnmanagedServices) extends Actor {

  private val logger: Logger = Logger(classOf[ServiceLocatorServer])

  import ServiceRegistryActor._

  private val registry: InternalRegistry = InternalRegistry.build(unmanagedServices)
  private val router: InternalRouter = new InternalRouter

  override def preStart(): Unit = {
    router.rebuild(registry)
  }

  override def receive: Receive = {

    // Service Locator operations
    case Remove(name) =>
      registry.remove(name)
      router.rebuild(registry)
    case Register(name, service) =>
      sender() ! registry.register(name, service)
      router.rebuild(registry)
    case Lookup(serviceName, portName) => sender() ! registry.lookup(serviceName, portName)
    case GetRegisteredServices =>
      val services: Seq[RegisteredService] = for {
        (serviceName, portName, uri) <- registry.list()
      } yield RegisteredService.of(serviceName.name, uri, portName.asJava)
      import scala.collection.JavaConverters._
      sender() ! RegisteredServices(TreePVector.from(services.asJava))

    // Service Gateway operations
    case route: Route =>
      sender() ! router.routeFor(route)
      router.warnOnAmbiguity(route)
  }

}

private class ServiceAlreadyRegistered(serviceName: String) extends TransportException(
  TransportErrorCode.PolicyViolation, s"A service with the same name=[$serviceName] was already registered"
) {
}
