/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package sbt

import sbt.internal.Load
import sbt.internal.LoadedBuild

object LagomLoad {
  object InjectSettings {
    def apply(
      global: Seq[Setting[_]],
      project: Seq[Setting[_]],
      projectLoaded: ClassLoader => Seq[Setting[_]],
    ): Load.InjectSettings = Load.InjectSettings(global, project, projectLoaded)
  }

  def buildConfigurations(
    loaded: LoadedBuild,
    rootProject: URI => String,
    injectSettings: Load.InjectSettings,
  ): Seq[Def.Setting[_]] = Load.buildConfigurations(loaded, rootProject, injectSettings)

  def defaultDelegates: LoadedBuild => Scope => Seq[Scope] = Load.defaultDelegates
}
