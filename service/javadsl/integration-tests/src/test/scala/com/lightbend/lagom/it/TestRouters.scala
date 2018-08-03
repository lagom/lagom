/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it

import java.util
import java.util.Optional
import javax.inject.Inject
import play.api.mvc
import play.api.mvc.{ request, _ }
import play.api.routing.Router.Routes
import play.api.routing.{ SimpleRouter, SimpleRouterImpl }
import play.core.j.JavaRouterAdapter
import play.mvc.Http
import play.routing.Router
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Builds a router that always respond with 'ping'.
 */
object PingRouter {
  def apply() = FixedResponseRouter("ping")
  def newInstanceJava() = new JavaRouterAdapter(apply())
}
/**
 * Builds a router that always respond with 'pong' and already prefixed with '/pong'.
 */
object PongRouter {
  def apply() = FixedResponseRouter("pong").withPrefix("/pong")
  def newInstanceJava() = new JavaRouterAdapter(apply())
}

/**
 * A Java Router to be wired by Guice that always respond with 'hello'
 */
class HelloRouter @Inject() (echo: Greeter) extends play.routing.Router {

  val underlying: play.api.routing.Router = FixedResponseRouter(echo.say())

  override def route(request: Http.RequestHeader): Optional[Handler] =
    Optional.ofNullable(underlying.routes.lift(request.asScala()).orNull)

  override def documentation(): util.List[Router.RouteDocumentation] = underlying.asJava.documentation()
  override def withPrefix(prefix: String): Router = underlying.asJava.withPrefix(prefix)
}

/**
 * A Java Router to be wired by Guice that always respond with '[prefixed] hello' and already prefixed with '/hello-prefixed'
 */
class PrefixedHelloRouter @Inject() (echo: Greeter) extends HelloRouter(echo) {
  override val underlying = FixedResponseRouter(s"[prefixed] ${echo.say()}").withPrefix("/hello-prefixed")
}

/** Just something to be injected and prove that DI is working for Routers */
class Greeter {
  def say() = "Hello"
}

/**
 * Builds a router that can be used in test, Scala or Java.
 *
 * The router is configured with a fixed message and always respond with the same message.
 * @return
 */
object FixedResponseRouter {

  def apply(msg: String) = new SimpleRouterImpl({
    case _ => new Action[Unit] {
      override def parser: BodyParser[Unit] = mvc.BodyParsers.utils.empty

      override def apply(request: Request[Unit]): Future[Result] =
        Future.successful(Results.Ok(msg))

      override def executionContext: ExecutionContext =
        scala.concurrent.ExecutionContext.global
    }
  })

  def newInstanceJava(msg: String) = new JavaRouterAdapter(FixedResponseRouter(msg))
}
