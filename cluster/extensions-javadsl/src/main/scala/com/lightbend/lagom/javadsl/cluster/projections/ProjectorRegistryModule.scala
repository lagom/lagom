/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.cluster.projections

import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry
import com.lightbend.lagom.javadsl.cluster.projections.DesiredState
import com.lightbend.lagom.javadsl.cluster.projections.DesiredStatus
import com.lightbend.lagom.javadsl.cluster.projections.Projections
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
class ProjectorRegistryModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ProjectorRegistry].toProvider[ProjectorRegistryProvider], // for internal use
    bind[Projections].to(classOf[ProjectionsImpl])                 // for users
  )
}

// This provider is trivial but required to keep ProjectorRegistry in `-core` and free of any Guice dependency
@Singleton
private[lagom] class ProjectorRegistryProvider @Inject()(actorSystem: ActorSystem ) extends Provider[ProjectorRegistry]{
  private val instance = new ProjectorRegistry(actorSystem)
  override def get(): ProjectorRegistry = instance
}

@Singleton
private class ProjectionsImpl @Inject()(impl: ProjectorRegistry)(implicit executionContext: ExecutionContext)
    extends Projections {
  import FutureConverters._
  override def getStatus(): CompletionStage[DesiredState] = {
    impl.getStatus().map(DesiredState.asJava).toJava
  }
}
