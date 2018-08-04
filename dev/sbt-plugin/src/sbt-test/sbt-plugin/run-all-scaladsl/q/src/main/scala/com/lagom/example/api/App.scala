package com.lagom.example.api

import com.lightbend.lagom.scaladsl.api.{LagomConfigComponent, ServiceAcl, ServiceInfo}
import com.lightbend.lagom.scaladsl.client.LagomServiceClientComponents
import com.softwaremill.macwire._
import org.slf4j.MDC
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router

import scala.collection.immutable
import scala.concurrent.ExecutionContext

final case class ServiceName(value: String) extends AnyVal

abstract class App(context: Context)
  extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with LagomServiceClientComponents
    with LagomConfigComponent
    with play.filters.HttpFiltersComponents {

  MDC.put("service_name", serviceInfo.serviceName)

  final lazy val serviceName: ServiceName = ServiceName(value = "LagomPlayServiceScala app")

  override lazy val serviceInfo: ServiceInfo = ServiceInfo(
    name = serviceName.value,
    locatableServices = Map(serviceName.value -> immutable.Seq(ServiceAcl.forPathRegex("(?!/api/).*")))
  )

  override implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
  override lazy val router: Router                              = controller.router

  final lazy val controller: Controller = wire[Controller]

}
