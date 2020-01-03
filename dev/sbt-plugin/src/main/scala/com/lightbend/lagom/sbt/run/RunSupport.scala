/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.sbt.run

import com.lightbend.lagom.dev.Reloader
import com.lightbend.lagom.sbt.Internal
import com.lightbend.lagom.sbt.LagomPlugin.autoImport._
import com.lightbend.lagom.sbt.LagomReloadableService.autoImport._
import sbt._
import sbt.Keys._

private[sbt] object RunSupport extends RunSupportCompat {

  def reloadRunTask(
      extraConfigs: Map[String, String]
  ): Def.Initialize[Task[Reloader.DevServer]] = Def.task {

    val state = Keys.state.value
    val scope = resolvedScoped.value.scope

    val reloadCompile = () =>
      RunSupport.compile(
        () => Project.runTask(lagomReload in scope, state).map(_._2).get,
        () => Project.runTask(lagomReloaderClasspath in scope, state).map(_._2).get,
        () => Project.runTask(streamsManager in scope, state).map(_._2).get.toEither.right.toOption
      )

    val classpath = (devModeDependencies.value ++ (externalDependencyClasspath in Runtime).value).distinct.files

    Reloader.startDevMode(
      scalaInstance.value.loader,
      classpath,
      reloadCompile,
      lagomClassLoaderDecorator.value,
      lagomWatchDirectories.value,
      lagomFileWatchService.value,
      baseDirectory.value,
      extraConfigs.toSeq ++ lagomDevSettings.value,
      lagomServiceAddress.value,
      selectHttpPortToUse.value,
      lagomServiceHttpsPort.value,
      RunSupport
    )
  }

  def nonReloadRunTask(
      extraConfigs: Map[String, String]
  ): Def.Initialize[Task[Reloader.DevServer]] = Def.task {

    val classpath = (devModeDependencies.value ++ (fullClasspath in Runtime).value).distinct

    val buildLinkSettings = extraConfigs.toSeq ++ lagomDevSettings.value
    Reloader.startNoReload(
      scalaInstance.value.loader,
      classpath.map(_.data),
      baseDirectory.value,
      buildLinkSettings,
      lagomServiceAddress.value,
      selectHttpPortToUse.value,
      lagomServiceHttpsPort.value
    )
  }

  /**
   * This task will calculate which port should be used for http.
   * To keep backward compatibility, we detect if the user have manually configured lagomServicePort in which
   * case we read the value set by the user and we don't use generated port.
   *
   * This task must be removed together with the deprecated lagomServicePort.
   */
  private def selectHttpPortToUse = Def.task {

    val logger                = Keys.sLog.value
    val deprecatedServicePort = lagomServicePort.value
    val serviceHttpPort       = lagomServiceHttpPort.value
    val generatedHttpPort     = lagomGeneratedServiceHttpPortCache.value
    val isUsingGeneratedPort  = serviceHttpPort == generatedHttpPort

    // deprecated setting was modified by user.
    if (deprecatedServicePort != -1 && isUsingGeneratedPort) {
      deprecatedServicePort
    } else if (deprecatedServicePort != -1 && !isUsingGeneratedPort) {
      logger.warn(
        s"Both 'lagomServiceHttpPort' ($serviceHttpPort) and 'lagomServicePort' ($deprecatedServicePort) are configured, 'lagomServicePort' will be ignored"
      )
      serviceHttpPort
    } else serviceHttpPort
  }

  private def devModeDependencies = Def.task {
    (managedClasspath in Internal.Configs.DevRuntime).value
  }
}
