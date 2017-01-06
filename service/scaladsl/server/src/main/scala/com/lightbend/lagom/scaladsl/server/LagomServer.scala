/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import akka.stream.Materializer
import com.lightbend.lagom.internal.scaladsl.server.{ ScaladslServerMacroImpl, ScaladslServiceRouter }
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceInfo }
import com.lightbend.lagom.scaladsl.client.ServiceResolver
import play.api.http.HttpConfiguration
import play.api.mvc.{ Handler, RequestHeader }
import play.api.routing.Router.Routes
import play.api.routing.{ Router, SimpleRouter }

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.language.experimental.macros

/**
 * A Lagom server
 */
sealed trait LagomServer {
  val name: String
  val serviceBindings: immutable.Seq[LagomServiceBinding[_]]
  def router: Router
}

object LagomServer {
  def forServices(bindings: LagomServiceBinding[_]*): LagomServer = {
    new LagomServer {
      override val serviceBindings: immutable.Seq[LagomServiceBinding[_]] = bindings.to[immutable.Seq]
      override val name: String = serviceBindings.headOption match {
        case Some(binding) => binding.descriptor.name
        case None          => throw new IllegalArgumentException
      }
      override lazy val router = new SimpleRouter {
        override val routes: Routes =
          serviceBindings.foldLeft(PartialFunction.empty[RequestHeader, Handler]) { (routes, serviceBinding) =>
            routes.orElse(serviceBinding.router.routes)
          }
        override def documentation: Seq[(String, String, String)] = serviceBindings.flatMap(_.router.documentation)
      }
    }
  }
}

trait LagomServerComponents {
  def httpConfiguration: HttpConfiguration
  def materializer: Materializer
  def executionContext: ExecutionContext
  def serviceResolver: ServiceResolver

  lazy val lagomServerBuilder: LagomServerBuilder = new LagomServerBuilder(httpConfiguration, serviceResolver)(materializer, executionContext)
  protected def bindService[T <: Service]: LagomServiceBinder[T] = macro ScaladslServerMacroImpl.createBinder[T]

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
  def buildRouter(service: Service): Router = {
    new ScaladslServiceRouter(serviceResolver.resolve(service.descriptor), service, httpConfiguration)(executionContext, materializer)
  }
}

sealed trait LagomServiceBinder[T <: Service] {
  def to(serviceFactory: => T): LagomServiceBinding[T]
}

object LagomServiceBinder {
  def apply[T <: Service: ClassTag](lagomServerBuilder: LagomServerBuilder, descriptor: Descriptor): LagomServiceBinder[T] = {
    val _descriptor = descriptor
    new LagomServiceBinder[T] {
      override def to(serviceFactory: => T): LagomServiceBinding[T] = {
        new LagomServiceBinding[T] {
          override lazy val router: Router = lagomServerBuilder.buildRouter(service)
          override lazy val service: T = serviceFactory
          override val descriptor: Descriptor = _descriptor
        }
      }
    }
  }
}

sealed trait LagomServiceBinding[T <: Service] {
  val descriptor: Descriptor
  def service: T
  def router: Router
}
