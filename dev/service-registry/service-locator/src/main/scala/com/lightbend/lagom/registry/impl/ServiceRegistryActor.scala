/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.net.InetSocketAddress
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
  case class Lookup(name: String)
  case class Remove(name: String)
  case class Register(name: String, service: ServiceRegistryService)
  case class Route(method: String, path: String)
  case object GetRegisteredServices
  case class RegisteredServices(services: PSequence[RegisteredService])
  sealed trait RouteResult
  case class Found(address: InetSocketAddress) extends RouteResult
  case class NotFound(registry: Map[String, ServiceRegistryService]) extends RouteResult
}

class ServiceRegistryActor @Inject() (unmanagedServices: UnmanagedServices) extends Actor {

  private val logger: Logger = Logger(classOf[ServiceLocatorServer])

  import ServiceRegistryActor._

  private var registry: Map[String, ServiceRegistryService] = unmanagedServices.services
  private var router = PartialFunction.empty[Route, InetSocketAddress]
  private var routerFunctions = Seq.empty[PartialFunction[Route, InetSocketAddress]]

  override def preStart(): Unit = {
    rebuildRouter()
  }

  override def receive: Receive = {
    case Lookup(name) => sender() ! registry.get(name).map(_.uri())
    case Remove(name) =>
      registry -= name
      rebuildRouter()
    case Register(name, service) =>
      registry.get(name) match {
        case None =>
          if (logger.isDebugEnabled) {
            logger.debug(s"Registering service [$name] with ACLs [${service.acls().asScala.map { acl => acl.toString }.mkString(", ")}] on ${service.uri()}).")
          }
          registry += (name -> service)
          rebuildRouter()
          sender() ! Done
        case Some(existing) =>
          if (existing == service)
            sender() ! Done // idempotent, same already registered
          else
            sender() ! Status.Failure(new ServiceAlreadyRegistered(name))
      }
    case GetRegisteredServices =>
      val services: List[RegisteredService] = (for {
        (name, service) <- registry
      } yield RegisteredService.of(name, service.uri))(collection.breakOut)
      import scala.collection.JavaConverters._
      sender() ! RegisteredServices(TreePVector.from(services.asJava))
    case route: Route =>
      sender() ! router.lift(route).fold[RouteResult](NotFound(registry))(Found.apply)
      warnOnAmbiguity(route)
  }

  private def warnOnAmbiguity(route: Route) = {
    if (logger.isWarnEnabled) {
      val servingRoutes = routerFunctions.filter(_.isDefinedAt(route))
      if (servingRoutes.size > 1) {
        val servers = servingRoutes.map(_.apply(route))
        logger.warn(s"Ambiguous route resolution serving route: $route. Route served by ${servers.head} but also matches ${servers.tail.mkString("[", ",", "]")}.")
      }
    }
  }

  private def serviceRouter(service: ServiceRegistryService) = {
    val addressUri = service.uri
    // lazy because if there's no ACLs, then there's no need to create an InetSocketAddress, and hence no need to fail
    // if the port can't be calculated.
    lazy val address = (addressUri.getScheme, addressUri.getHost, addressUri.getPort) match {
      case (_, null, _)        => throw new IllegalArgumentException("Cannot register a URI that doesn't have a host: " + addressUri)
      case ("http", host, -1)  => new InetSocketAddress(host, 80)
      case ("https", host, -1) => new InetSocketAddress(host, 443)
      case (_, _, -1)          => throw new IllegalArgumentException("Cannot register a URI that does not specify a port: " + addressUri)
      case (_, host, port)     => new InetSocketAddress(host, port)
    }
    val routerFunctions: Seq[PartialFunction[Route, InetSocketAddress]] = service.acls.asScala.map {
      case acl =>
        acl.method().asScala -> acl.pathRegex().asScala.map(Pattern.compile)
    }.map {
      case (aclMethod, pathRegex) =>
        val pf: PartialFunction[Route, InetSocketAddress] = {
          case Route(method, path) if aclMethod.forall(_.name == method) && pathRegex.forall(_.matcher(path).matches()) =>
            address
        }
        pf
    }
    routerFunctions
  }

  private def rebuildRouter() = {
    routerFunctions = registry.values.flatMap(serviceRouter).toSeq
    router = routerFunctions.foldLeft(PartialFunction.empty[Route, InetSocketAddress])(_ orElse _)
  }

}

private class ServiceAlreadyRegistered(serviceName: String) extends TransportException(
  TransportErrorCode.PolicyViolation, s"A service with the same name=[$serviceName] was already registered"
) {
}
