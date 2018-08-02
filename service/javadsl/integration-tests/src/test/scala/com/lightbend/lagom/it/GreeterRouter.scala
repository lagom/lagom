/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it

import java.util
import java.util.Optional

import javax.inject.Inject
import play.api.mvc
import play.api.mvc._
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.core.j.JavaRouterAdapter
import play.mvc.Http
import play.routing.Router

import scala.concurrent.{ ExecutionContext, Future }

class GreeterRouter @Inject() (echo: Greeter) extends play.routing.Router {

  override def documentation(): util.List[Router.RouteDocumentation] = new util.ArrayList[Router.RouteDocumentation]()

  override def route(request: Http.RequestHeader): Optional[Handler] = {
    val handler = new Action[Unit] {
      override def parser: BodyParser[Unit] = mvc.BodyParsers.utils.empty

      override def apply(request: Request[Unit]): Future[Result] =
        Future.successful(Results.Ok(echo.say()))

      override def executionContext: ExecutionContext =
        scala.concurrent.ExecutionContext.global
    }
    Optional.ofNullable(handler)
  }

  override def withPrefix(prefix: String): Router = new JavaRouterAdapter(asScala.withPrefix(prefix))
}

class Greeter {
  def say() = "Hello"
}
