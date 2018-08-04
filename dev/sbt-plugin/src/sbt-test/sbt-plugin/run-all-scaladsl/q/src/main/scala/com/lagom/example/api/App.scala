package com.lagom.example.api

import com.lightbend.lagom.scaladsl.api.{LagomConfigComponent, ServiceAcl, ServiceInfo}
import com.lightbend.lagom.scaladsl.client.LagomServiceClientComponents
import com.softwaremill.macwire._
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router

abstract class App(context: Context)
  extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with LagomServiceClientComponents
    with LagomConfigComponent
    with play.filters.HttpFiltersComponents {

  final val serviceName = "LagomPlayServiceScala app"

  override final lazy val serviceInfo: ServiceInfo = ServiceInfo(
    name = serviceName,
    locatableServices = Map(serviceName -> (ServiceAcl.forPathRegex("(?!/api/).*") :: Nil))
  )

  final lazy val controller: Controller = wire[Controller]

  override final lazy val router: Router = Router.from(controller.routes)

}
