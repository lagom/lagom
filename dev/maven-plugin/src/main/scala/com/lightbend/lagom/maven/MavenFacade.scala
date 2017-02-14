/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import java.io.File
import java.util
import java.util.Collections
import javax.inject.{ Inject, Singleton }

import org.apache.maven.RepositoryUtils
import org.apache.maven.artifact.ArtifactUtils
import org.apache.maven.execution.MavenSession
import org.apache.maven.lifecycle.internal._
import org.apache.maven.model.Plugin
import org.apache.maven.plugin.{ BuildPluginManager, MojoExecution }
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.configuration.PlexusConfiguration
import org.codehaus.plexus.util.StringUtils
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.eclipse.aether.{ DefaultRepositorySystemSession, RepositorySystem }
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.{ WorkspaceReader, WorkspaceRepository }
import org.eclipse.aether.resolution.{ DependencyRequest, DependencyResult }

import scala.collection.JavaConverters._

/**
 * Facade in front of maven.
 *
 * All the hairy stuff goes here.
 */
@Singleton
class MavenFacade @Inject() (repoSystem: RepositorySystem, session: MavenSession,
    buildPluginManager: BuildPluginManager, lifecycleExecutionPlanCalculator: LifecycleExecutionPlanCalculator,
    logger: MavenLoggerProxy) {

  /**
   * Resolve the classpath for the given artifact.
   *
   * @return The classpath.
   */
  def resolveArtifact(artifact: Artifact): Seq[Artifact] = {
    resolveDependency(new Dependency(artifact, "runtime"))
  }

  /**
   * Resolve the classpath for the given dependency.
   */
  def resolveDependency(dependency: Dependency, additionalDependencies: Seq[Dependency] = Nil): Seq[Artifact] = {
    val collect = new CollectRequest()
    collect.setRoot(dependency)
    collect.setRepositories(session.getCurrentProject.getRemoteProjectRepositories)
    additionalDependencies.foreach(collect.addDependency)

    toDependencies(resolveDependencies(collect)).map(_.getArtifact)
  }

  /**
   * Resolve a project, with additional dependencies added to the project.
   *
   * @param project The project to resolve.
   * @param additionalDependencies The additional dependencies to add.
   * @return The resolved project.
   */
  def resolveProject(project: MavenProject, additionalDependencies: Seq[Dependency]): Seq[Dependency] = {

    // We use a low level API rather than just resolving the project so we can inject our own dev mode dependencies
    // The implementation of this is modelled off org.apache.maven.project.DefaultProjectDependenciesResolver
    val collect = new CollectRequest()
    collect.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact))
    collect.setRequestContext("dev-mode")
    collect.setRepositories(project.getRemoteProjectRepositories)

    val stereotypes = session.getRepositorySession.getArtifactTypeRegistry

    // Add project dependencies
    project.getDependencies.asScala.foreach { dep =>
      if (!(
        StringUtils.isEmpty(dep.getGroupId) ||
        StringUtils.isEmpty(dep.getArtifactId) ||
        StringUtils.isEmpty(dep.getVersion)
      )) {
        collect.addDependency(RepositoryUtils.toDependency(dep, stereotypes))
      }
    }

    // Add additional dependencies
    additionalDependencies.foreach(collect.addDependency)

    val depMngt = project.getDependencyManagement
    if (depMngt != null) {
      depMngt.getDependencies.asScala.foreach { dep =>
        collect.addManagedDependency(RepositoryUtils.toDependency(dep, stereotypes))
      }
    }

    val depResult = resolveDependencies(collect)

    // The code below comes from org.apache.maven.project.DefaultProjectBuilder
    val artifacts = new util.LinkedHashSet[org.apache.maven.artifact.Artifact]
    if (depResult.getRoot != null) {
      RepositoryUtils.toArtifacts(artifacts, depResult.getRoot.getChildren, Collections.singletonList(project.getArtifact.getId), null)
      val lrm = session.getRepositorySession.getLocalRepositoryManager
      artifacts.asScala.foreach { artifact =>
        if (!artifact.isResolved) {
          val path = lrm.getPathForLocalArtifact(RepositoryUtils.toArtifact(artifact))
          artifact.setFile(new File(lrm.getRepository.getBasedir, path))
        }
      }
    }

    if (additionalDependencies.isEmpty) {
      project.setResolvedArtifacts(artifacts)
      project.setArtifacts(artifacts)
    }

    toDependencies(depResult)
  }

  private def resolveDependencies(collect: CollectRequest): DependencyResult = {
    val depRequest = new DependencyRequest(collect, null)

    // Replace the workspace reader with one that will resolve projects that haven't been compiled yet
    val repositorySession = new DefaultRepositorySystemSession(session.getRepositorySession)
    repositorySession.setWorkspaceReader(new UnbuiltWorkspaceReader(repositorySession.getWorkspaceReader, session))

    val collectResult = repoSystem.collectDependencies(repositorySession, collect)

    val node = collectResult.getRoot
    depRequest.setRoot(node)

    repoSystem.resolveDependencies(repositorySession, depRequest)
  }

  private def toDependencies(depResult: DependencyResult): Seq[Dependency] = {
    depResult.getArtifactResults.asScala.map(_.getRequest.getDependencyNode.getDependency)
  }

  def locateServices: Seq[MavenProject] = {
    session.getAllProjects.asScala.filter(isService)
  }

  private def isService(project: MavenProject): Boolean = {
    // If the value is set, return it
    isLagomOrPlayService(project).getOrElse {

      // Otherwise try and run lagom:configure
      if (executeMavenPluginGoal(project, "configure")) {

        // Now try and get the value
        isLagomOrPlayService(project).getOrElse {

          // The value should have been set by lagom:configure, fail
          sys.error(s"${LagomKeys.LagomService} not set on project ${project.getArtifactId} after running configure!")
        }
      } else {
        // Lagom plugin not configured, return false
        logger.debug(s"Project ${project.getArtifactId} is not a Lagom service because it doesn't have the Lagom plugin")
        LagomKeys.LagomService.put(project, false)
        LagomKeys.PlayService.put(project, false)
        false
      }
    }
  }

  private def isLagomOrPlayService(project: MavenProject): Option[Boolean] = {
    LagomKeys.LagomService.get(project).flatMap {
      case true => Some(true)
      case false => LagomKeys.PlayService.get(project)
    }
  }

  /**
   * Execute the given Lagom plugin goal on the given project.
   *
   * @return True if the plugin goal was found and executed
   */
  def executeMavenPluginGoal(project: MavenProject, name: String): Boolean = {
    getLagomPlugin(project) match {
      case Some(plugin) =>
        val pluginDescriptor = buildPluginManager.loadPlugin(plugin, project.getRemotePluginRepositories,
          session.getRepositorySession)

        val mojoDescriptor = Option(pluginDescriptor.getMojo(name)).getOrElse {
          sys.error(s"Could not find goal $name on Lagom maven plugin")
        }

        val mojoExecution = new MojoExecution(mojoDescriptor, "lagom-internal-request", MojoExecution.Source.CLI)
        lifecycleExecutionPlanCalculator.setupMojoExecution(session, project, mojoExecution)

        switchProject(project) {
          buildPluginManager.executeMojo(session, mojoExecution)
          true
        }

      case _ => false
    }
  }

  def executeLifecyclePhase(projects: Seq[MavenProject], phase: String): Unit = {
    projects.foreach { project =>
      switchProject(project) {
        // Calculate an execution plan
        val executionPlan = lifecycleExecutionPlanCalculator.calculateExecutionPlan(session, project,
          Collections.singletonList(new LifecycleTask(phase)))

        // Execute it
        executionPlan.asScala.foreach { mojoExecution =>
          buildPluginManager.executeMojo(session, mojoExecution.getMojoExecution)
        }
      }
    }
  }

  private def switchProject[T](project: MavenProject)(block: => T): T = {
    val currentProject = session.getCurrentProject
    if (currentProject != project) {
      try {
        session.setCurrentProject(project)
        block
      } finally {
        session.setCurrentProject(currentProject)
      }
    } else {
      block
    }
  }

  /**
   * Converts PlexusConfiguration to an Xpp3Dom.
   */
  private def plexusConfigurationToXpp3Dom(config: PlexusConfiguration): Xpp3Dom = {
    val result = new Xpp3Dom(config.getName)
    result.setValue(config.getValue(null))
    config.getAttributeNames.foreach { name =>
      result.setAttribute(name, config.getAttribute(name))
    }
    config.getChildren.foreach { child =>
      result.addChild(plexusConfigurationToXpp3Dom(child))
    }
    result
  }

  private def getLagomPlugin(project: MavenProject): Option[Plugin] = {
    Option(project.getPlugin("com.lightbend.lagom:lagom-maven-plugin"))
  }

}

/**
 * A workspace reader that always resolves to the output directories of projects, even when they aren't built.
 *
 * The default maven workspace reader will prefer to resolve the jar file if it exists, and will not resolve if the
 * project has not yet been compiled.
 */
class UnbuiltWorkspaceReader(delegate: WorkspaceReader, session: MavenSession) extends WorkspaceReader {
  override def findVersions(artifact: Artifact): util.List[String] = {
    delegate.findVersions(artifact)
  }

  override def getRepository: WorkspaceRepository = delegate.getRepository

  override def findArtifact(artifact: Artifact): File = {
    val projectKey = ArtifactUtils.key(artifact.getGroupId, artifact.getArtifactId, artifact.getVersion)

    session.getProjectMap.get(projectKey) match {
      case null => null
      case project =>
        artifact.getExtension match {
          case "pom" => project.getFile
          case "jar" if project.getPackaging == "jar" =>
            if (isTestArtifact(artifact)) {
              new File(project.getBuild.getTestOutputDirectory)
            } else {
              new File(project.getBuild.getOutputDirectory)
            }
          case _ =>
            delegate.findArtifact(artifact)
        }
    }
  }

  private def isTestArtifact(artifact: Artifact): Boolean = {
    ("test-jar" == artifact.getProperty("type", "")) ||
      ("jar" == artifact.getExtension && "tests" == artifact.getClassifier)
  }
}
