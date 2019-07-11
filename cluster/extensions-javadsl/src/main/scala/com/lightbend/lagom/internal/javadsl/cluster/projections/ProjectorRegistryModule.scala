/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.cluster.projections

import java.util.concurrent.CompletionStage

import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistry
import com.lightbend.lagom.javadsl.cluster.projections.DesiredStatus
import com.lightbend.lagom.javadsl.cluster.projections.Projections
import javax.inject.Inject
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext

class ProjectorRegistryModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ProjectorRegistry].toSelf.eagerly(),          // for internal use
    bind[Projections].to(classOf[ProjectionsImpl]) // for users
  )
}

@Inject
private class ProjectionsImpl(impl: ProjectorRegistry)(implicit executionContext: ExecutionContext)
    extends Projections {
  import FutureConverters._
  override def getStatus(): CompletionStage[DesiredStatus] = {
    impl.getStatus().map(DesiredStatus.asJava).toJava
  }
}
