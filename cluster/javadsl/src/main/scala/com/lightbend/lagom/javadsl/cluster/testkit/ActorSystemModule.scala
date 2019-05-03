/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.testkit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

import scala.concurrent.ExecutionContext

// TODO: This is not production code. It's only used in tests and it's never run in PROD.
class ActorSystemModule(system: ActorSystem) extends Module {

  private lazy val mat = ActorMaterializer()(system)

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ActorSystem].to(system),
    bind[Materializer].to(mat),
    bind[ExecutionContext].to(system.dispatcher)
  )

}
