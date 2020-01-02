/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.maven

import javax.inject.Inject
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo

import scala.beans.BeanProperty

/**
 * Internal goal, invoked by other Lagom mojos that work with multiple projects at once, to read plugin configuration
 * for a project and set up the projects context values.
 */
class ConfigureMojo @Inject()(session: MavenSession) extends AbstractMojo {

  @BeanProperty
  var lagomService: Boolean = _

  @BeanProperty
  var playService: Boolean = _

  override def execute(): Unit = {
    LagomKeys.LagomService.put(session.getCurrentProject, lagomService)
    LagomKeys.PlayService.put(session.getCurrentProject, playService)
  }
}
