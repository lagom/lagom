/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import akka.stream.Materializer
import com.lightbend.lagom.internal.scaladsl.server.{ ScaladslServerMacroImpl, ScaladslServiceRouter }
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceInfo }
import com.lightbend.lagom.scaladsl.client.ServiceResolver
import com.lightbend.lagom.scaladsl.server.status.MetricsServiceComponents
import play.api.http.HttpConfiguration
import play.api.mvc.{ Handler, RequestHeader }
import play.api.routing.Router.Routes
import play.api.routing.{ Router, SimpleRouter }

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.language.experimental.macros
import scala.reflect.ClassTag

/**
 * A Lagom server
 */
sealed trait LagomServer {
  val name: String
  // TODO: replace with LagomServiceBinding[_] when removing LagomServer.forServices
  val serviceBindings: immutable.Seq[LagomServiceBinding[_]]
  def router: LagomServiceRouter
}

/**
 * A Lagom service router.
 *
 * This trait doesn't add anything, except that it makes the router created by the LagomServer
 * strongly typed. This allows it to be dependency injected by type, making it simple to use it
 * in combination with the Play routes file.
 *
 * For example, if using a custom router, the Lagom router could be routed to from the routes file
 * like this:
 *
 * ```
 * ->   /     com.lightbend.lagom.scaladsl.server.LagomServiceRouter
 * ```
 */
trait LagomServiceRouter extends Router

object LagomServer {
  @deprecated("Binding multiple locatable ServiceDescriptors per Lagom service is unsupported. Use LagomServerComponents.serverFor instead", "1.3.3")
  def forServices(bindings: LagomServiceBinding[_]*): LagomServer = {
    new LagomServer {
      override val serviceBindings: immutable.Seq[LagomServiceBinding[_]] = bindings.to[immutable.Seq]
      override val name: String = serviceBindings.headOption match {
        case Some(binding) => binding.descriptor.name
        case None          => throw new IllegalArgumentException
      }
      override lazy val router = new SimpleRouter with LagomServiceRouter {
        override val routes: Routes =
          serviceBindings.foldLeft(PartialFunction.empty[RequestHeader, Handler]) { (routes, serviceBinding) =>
            routes.orElse(serviceBinding.router.routes)
          }
        override def documentation: Seq[(String, String, String)] = serviceBindings.flatMap(_.router.documentation)
      }
    }
  }

  /**
   * Internal API used by AST generated by the serverFor macro.
   */
  def forService(binding: LagomServiceBinding[_]): LagomServer = {
    new LagomServer {
      override val serviceBindings = List[LagomServiceBinding[_]](binding)
      override val name = binding.descriptor.name
      override lazy val router = binding.router
    }
  }
}

trait LagomServerComponents extends MetricsServiceComponents {
  def httpConfiguration: HttpConfiguration
  def materializer: Materializer
  def executionContext: ExecutionContext
  def serviceResolver: ServiceResolver

  lazy val lagomServerBuilder: LagomServerBuilder = new LagomServerBuilder(httpConfiguration, serviceResolver)(materializer, executionContext)
  protected def bindService[T <: Service]: LagomServiceBinder[T] = macro ScaladslServerMacroImpl.createBinder[T]

  /**
   * Bind a server for the given service and factory for the service.
   *
   * Note, the type parameter for this method should always be passed explicitly, as the macro needs it to know what
   * the trait for the service descriptor should be.
   */
  protected def serverFor[T <: Service](serviceFactory: => T): LagomServer = macro ScaladslServerMacroImpl.simpleBind[T]

  lazy val serviceInfo: ServiceInfo = {
    val locatableServices = lagomServer.serviceBindings.map(_.descriptor).collect {
      case locatable if locatable.locatableService =>
        val resolved = serviceResolver.resolve(locatable)
        resolved.name -> resolved.acls
    }.toMap
    ServiceInfo(lagomServer.name, locatableServices)
  }
  lazy val router: Router = lagomServer.router

  def lagomServer: LagomServer
}

final class LagomServerBuilder(httpConfiguration: HttpConfiguration, serviceResolver: ServiceResolver)(implicit materializer: Materializer, executionContext: ExecutionContext) {
  def buildRouter(service: Service): LagomServiceRouter = {
    new ScaladslServiceRouter(serviceResolver.resolve(service.descriptor), service, httpConfiguration)(executionContext, materializer)
  }
}

/**
 * Internal API used by AST generated by the serverFor macro.
 */
sealed trait LagomServiceBinder[T <: Service] {
  def to(serviceFactory: => T): LagomServiceBinding[T]
}

/**
 * Internal API used by AST generated by the serverFor macro.
 */
object LagomServiceBinder {
  def apply[T <: Service: ClassTag](lagomServerBuilder: LagomServerBuilder, descriptor: Descriptor): LagomServiceBinder[T] = {
    val _descriptor = descriptor
    new LagomServiceBinder[T] {
      override def to(serviceFactory: => T): LagomServiceBinding[T] = {
        new LagomServiceBinding[T] {
          override lazy val router: LagomServiceRouter = lagomServerBuilder.buildRouter(service)
          override lazy val service: T = serviceFactory
          override val descriptor: Descriptor = _descriptor
        }
      }
    }
  }
}

/**
 * Internal API used by AST generated by the serverFor macro.
 */
sealed trait LagomServiceBinding[T <: Service] {
  val descriptor: Descriptor
  def service: T
  def router: LagomServiceRouter
}
