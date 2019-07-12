/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections

import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.cluster.projections.ProjectionRegistry
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext

@ApiMayChange
class ProjectionRegistryModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ProjectionRegistry].toProvider[ProjectionRegistryProvider], // for internal use
    bind[Projections].to(classOf[ProjectionsImpl])                 // for users
  )
}

// This provider is trivial but required to keep ProjectionRegistry in `-core` and free of any Guice dependency
@Singleton
private[lagom] class ProjectionRegistryProvider @Inject()(actorSystem: ActorSystem ) extends Provider[ProjectionRegistry]{
  private val instance = new ProjectionRegistry(actorSystem)
  override def get(): ProjectionRegistry = instance
}

@Singleton
private class ProjectionsImpl @Inject()(impl: ProjectionRegistry)(implicit executionContext: ExecutionContext)
    extends Projections {
  import FutureConverters._
  override def getStatus(): CompletionStage[DesiredState] = {
    impl.getStatus().map(DesiredState.asJava).toJava
  }
}
