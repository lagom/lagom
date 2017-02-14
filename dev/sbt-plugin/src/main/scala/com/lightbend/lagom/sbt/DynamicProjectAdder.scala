/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt.Keys._
import sbt.Setting
import sbt._

object DynamicProjectAdder {

  /**
   * Add a list of projects to the state.
   *
   * The projects should define all their settings (note, autoplugins will not be applied here, the project should
   * manually add all settings it needs, including things from the CorePlugin, IvyPlugin etc).  They also will most
   * likely need to pass a non nil set of configurations.  The project id is important, but the project base directory
   * is not important unless you want it to be - the target directory for the build will be redefined to be something
   * in the main target directory.
   */
  def addProjects(projects: Project*)(state: State): State = {
    val extracted = Project.extract(state)
    import extracted._

    val undefinedProjects = projects.filterNot(project => currentUnit.defined.contains(project.id))

    if (undefinedProjects.nonEmpty) {
      val base = extracted.get(Keys.baseDirectory in ThisBuild)

      val projectsAndSettings = undefinedProjects.map { project =>

        // Redefine the project root to be one in the target directory, this is a phantom project
        val projectRoot = base / "target" / "lagom-dynamic-projects" / project.id
        val projectWithRoot: Project = project.copy(base = projectRoot)
        val projectRef = ProjectRef(structure.root, project.id)

        // Now we resolve the project. I don't know what that means, but it needs to be done because the type system
        // says so.
        val resolvedProject = projectWithRoot.resolve(Scope.resolveProjectRef(structure.root, structure.rootProject, _))

        // Some really basic config that's apparently needed for any project to do anything.
        val defineConfig: Seq[Setting[_]] = for (c <- resolvedProject.configurations) yield (configuration in (projectRef, ConfigKey(c.name))) := c
        val builtin: Seq[Setting[_]] = (thisProject := resolvedProject) +: (thisProjectRef := projectRef) +: defineConfig
        // And put all the settings together
        val settings = builtin ++ projectWithRoot.settings

        // Now transform the settings. This transforms things like target := projectTarget to
        // target in projectRef := projectTarget
        val transformedSettings = Load.transformSettings(Load.projectScope(projectRef), currentRef.build, rootProject, settings)

        resolvedProject -> transformedSettings
      }

      // A build unit has a map of defined project ids to defined projects, we create that map.
      val newDefinedProjects = projectsAndSettings.foldLeft(currentUnit.defined) {
        case (defined, (project, _)) => defined + (project.id -> project)
      }

      // And we create the new build unit.
      val unitWithNewProjects = new LoadedBuildUnit(currentUnit.unit, newDefinedProjects, currentUnit.rootProjects,
        currentUnit.buildSettings)
      // And now we create the new build, which has multiple build units, the units being a map of URIs to the build
      // base directories to the LoadedBuildUnit objects.  Most projects only have one build unit, but I think if you
      // use ProjectRefs to external builds you end up with multiple.
      val buildWithNewProjects = new LoadedBuild(structure.root, structure.units + (currentUnit.unit.uri -> unitWithNewProjects))
      // Delegates are important, delegates are what are used to look up settings, if you execute or depend on
      // foo/compile:task, it uses delegates to look up task from the compile config from the foo scope, so we need to
      // recalculate these because we're adding new projects.
      val delegatesWithNewProjects = Load.defaultDelegates(buildWithNewProjects)
      // And now create the new build structure, which has the new build unit and the new delegates. At this point, the
      // data, which is all the settings and tasks and anything, is not yet generated, that's ok.
      val structureWithNewProject = new BuildStructure(
        buildWithNewProjects.units, structure.root, structure.settings, structure.data,
        structure.index, structure.streams, delegatesWithNewProjects, structure.scopeLocal
      )

      // Compile all the new settings for each new project, and we also need to redefine the loadedBuild setting,
      // because lots of things use this to look up lists of projects.
      val allNewSettings = projectsAndSettings.foldLeft(Seq.empty[Setting[_]]) {
        case (settings, (_, projectSettings)) => settings ++ projectSettings
      } :+ (loadedBuild in GlobalScope := buildWithNewProjects)

      // This is actually done by Load.reapply, but IntelliJ sbt support doesn't work without this, probably because
      // it's doing something dodgy. Applying the final transforms twice doesn't hurt.
      val transformed = Load.finalTransforms(allNewSettings)

      // Now we create a new session with the new settings.
      val newSession = session.appendRaw(transformed)

      // Now we recreate the structure, this is where the structure data is calculated, which evaluates all the settings
      // and works out all the dependencies.
      val reindexedStructure = Load.reapply(newSession.mergeSettings, structureWithNewProject)

      // And finally, put all the new stuff in a new state.
      state.copy(attributes = state.attributes.put(stateBuildStructure, reindexedStructure).put(sessionSettings, newSession))
    } else {
      state
    }

  }

}
