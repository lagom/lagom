/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it.routers

import javax.inject.Inject
import play.api.mvc
import play.api.mvc._
import play.api.routing.Router.Routes
import play.api.routing.Router
import play.api.routing.SimpleRouterImpl
import play.core.j.JavaRouterAdapter

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Builds a router that always respond with 'ping'.
 */
object PingRouter {
  def apply()       = FixedResponseRouter("ping")
  def newInstance() = apply()
}

/**
 * Builds a router that always respond with 'pong' and already prefixed with '/pong'.
 */
object PongRouter {
  def apply()       = FixedResponseRouter("pong").withPrefix("/pong")
  def newInstance() = apply()
}

/**
 * A Router to be wired by Guice that always respond with 'hello'
 */
class HelloRouter @Inject() (echo: Greeter) extends Router {

  val underlying: play.api.routing.Router = FixedResponseRouter(echo.say())

  override def routes: Routes = underlying.routes

  override def documentation: Seq[(String, String, String)] = underlying.documentation

  override def withPrefix(prefix: String): Router = underlying.withPrefix(prefix)
}

/**
 * A Router to be wired by Guice that always respond with '[prefixed] hello' and already prefixed with '/hello-prefixed'
 */
class PrefixedHelloRouter @Inject() (echo: Greeter) extends HelloRouter(echo) {
  override val underlying = FixedResponseRouter(s"[prefixed] ${echo.say()}").withPrefix("/hello-prefixed")
}

/** Just something to be injected and prove that DI is working for Routers */
class Greeter {
  def say() = "Hello"
}

/**
 * Builds a router that can be used in test.
 *
 * The router is configured with a fixed message and always respond with the same message.
 * @return
 */
object FixedResponseRouter {

  def apply(msg: String) =
    new SimpleRouterImpl({
      case _ =>
        new Action[Unit] {
          override def parser: BodyParser[Unit] = mvc.BodyParsers.utils.empty

          override def apply(request: Request[Unit]): Future[Result] =
            Future.successful(Results.Ok(msg))

          override def executionContext: ExecutionContext =
            scala.concurrent.ExecutionContext.global
        }
    })

  def newInstanceJava(msg: String) = new JavaRouterAdapter(FixedResponseRouter(msg))
}
