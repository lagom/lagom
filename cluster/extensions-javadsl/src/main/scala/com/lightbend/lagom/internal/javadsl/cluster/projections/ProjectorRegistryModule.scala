/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.cluster.projections

import java.util
import java.util.concurrent.CompletionStage

import com.lightbend.lagom.internal.cluster.projections.ProjectorRegistryImpl
import com.lightbend.lagom.javadsl.cluster.projections.ProjectorRegistry
import javax.inject.Inject
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

import scala.concurrent.ExecutionContext

class ProjectorRegistryModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ProjectorRegistryImpl].toSelf.eagerly(), // for internal use
    bind[ProjectorRegistry].to(classOf[ProjectorRegistryJavaImpl]) // for users
  )
}

@Inject
private class ProjectorRegistryJavaImpl(impl: ProjectorRegistryImpl)(implicit executionContext: ExecutionContext)
    extends ProjectorRegistry {

  import scala.collection.JavaConverters._
  import scala.compat.java8.FutureConverters._

  override def getStatus()
      : CompletionStage[util.Map[ProjectorRegistryImpl.ProjectionMetadata, ProjectorRegistryImpl.ProjectorStatus]] = {
    impl.getStatus().map(_.asJava).toJava
  }
}
