/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.cluster.testkit

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

import scala.concurrent.ExecutionContext

class ActorSystemModule(system: ActorSystem) extends Module {

  private lazy val mat = ActorMaterializer()(system)

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ActorSystem].to(system),
    bind[Materializer].to(mat),
    bind[ExecutionContext].to(system.dispatcher)
  )

}
