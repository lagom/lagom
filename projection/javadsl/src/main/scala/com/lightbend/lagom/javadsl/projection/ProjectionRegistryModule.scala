/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.projection

import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import com.lightbend.lagom.internal.projection.ProjectionRegistry
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.projection.State
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext

/**
 * Provides an instance of `Projections` to interact with the (internal) `ProjectionRegistry`.
 */
@ApiMayChange
class ProjectionRegistryModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[Projections].to(classOf[ProjectionsImpl])                   // for end users
  )
}

@Singleton
@InternalApi
/** INTERNAL API */
private class ProjectionsImpl @Inject()(actorSystem: ActorSystem)(
implicit executionContext: ExecutionContext
) extends Projections {
  private val registry:  ProjectionRegistry = new ProjectionRegistry(actorSystem)

  import FutureConverters._

  override def getStatus(): CompletionStage[State] =
    registry.getState().toJava

  override def stopAllWorkers(projectionName: String): Unit =
    registry.stopAllWorkers(projectionName)

  override def stopWorker(projectionName: String, tagName: String): Unit =
    registry.stopWorker(WorkerCoordinates(projectionName, tagName))

  override def startAllWorkers(projectionName: String): Unit =
    registry.startAllWorkers(projectionName)

  override def startWorker(projectionName: String, tagName: String): Unit =
    registry.startWorker(WorkerCoordinates(projectionName, tagName))

}
