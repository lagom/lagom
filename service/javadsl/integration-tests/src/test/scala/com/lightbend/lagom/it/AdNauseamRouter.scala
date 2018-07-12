/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it

import akka.util.ByteString
import play.api.libs.streams.Accumulator
import play.api.mvc
import play.api.mvc._
import play.api.routing.SimpleRouterImpl
import play.core.j.JavaRouterAdapter

import scala.concurrent.{ ExecutionContext, Future }
/**
 * Builds a router that can be used in test, Scala or Java.
 *
 * The router is configured with a fixed message and always respond with the same message, ad nauseam.
 * @return
 */
object AdNauseamRouter {

  def apply(msg: String) = new SimpleRouterImpl({
    case _ => new Action[Unit] {
      override def parser: BodyParser[Unit] = mvc.BodyParsers.utils.empty

      override def apply(request: Request[Unit]): Future[Result] =
        Future.successful(Results.Ok(msg))

      override def executionContext: ExecutionContext =
        scala.concurrent.ExecutionContext.global
    }
  })

  def newInstanceJava(msg: String) = new JavaRouterAdapter(AdNauseamRouter(msg))
}
