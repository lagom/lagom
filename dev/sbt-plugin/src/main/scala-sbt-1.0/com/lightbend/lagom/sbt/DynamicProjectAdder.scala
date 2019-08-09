/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.sbt

import sbt._
import sbt.Keys._
import sbt.internal.LoadedBuild
import sbt.internal.LoadedBuildUnit
import sbt.internal.BuildStructure

object DynamicProjectAdder {
  def addProjects(projects: Project*)(state: State): State = {
    val extracted = Project.extract(state)
    import extracted._, currentUnit._
    if (projects.forall(p => defined.contains(p.id)))
      return state
    val base         = get(baseDirectory in ThisBuild) / "target" / "lagom-dynamic-projects"
    val resolveRef   = Scope.resolveProjectRef(structure.root, rootProject, _)
    val newProjects  = projects.iterator.map(p => p.in(base / p.id).resolve(resolveRef))
    val newDefined   = newProjects.foldLeft(defined)((map, p) => map.updated(p.id, p))
    val newUnit      = new LoadedBuildUnit(unit, newDefined, rootProjects, buildSettings)
    val newUnits     = structure.units.updated(newUnit.unit.uri, newUnit)
    val newBuild     = new LoadedBuild(structure.root, newUnits)
    val newDelegates = LagomLoad.defaultDelegates(newBuild)
    val noInject     = LagomLoad.InjectSettings(Nil, Nil, _ => Nil)
    val newSettings  = LagomLoad.buildConfigurations(newBuild, rootProject, noInject)
    val newStructure = {
      import structure._
      new BuildStructure(newUnits, root, settings, data, index, streams, newDelegates, scopeLocal)
    }
    Extracted(newStructure, session, currentRef).appendWithSession(newSettings, state)
  }
}
