/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import java.io.File
import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.core.LagomVersion
import com.lightbend.lagom.dev.PortAssigner.{ Port, PortRange, ProjectName }
import com.lightbend.lagom.dev.Reloader.{ CompileFailure, CompileResult, CompileSuccess, DevServer }
import com.lightbend.lagom.dev.{ LagomConfig, PortAssigner, Reloader }
import org.apache.maven.Maven
import org.apache.maven.artifact.ArtifactUtils
import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.eclipse.aether.artifact.{ Artifact, DefaultArtifact }
import org.eclipse.aether.graph.Dependency
import play.api.PlayException
import play.dev.filewatch.FileWatchService

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/**
 * Manages services
 */
@Singleton
class ServiceManager @Inject() (logger: MavenLoggerProxy, session: MavenSession, facade: MavenFacade,
    scalaClassLoaderManager: ScalaClassLoaderManager) {

  private var runningServices = Map.empty[MavenProject, DevServer]
  private var runningExternalProjects = Map.empty[Dependency, DevServer]
  private var portMap: Option[Map[ProjectName, Port]] = None

  private def detectScalaBinaryVersion(artifacts: Seq[Artifact]) = {
    artifacts.collectFirst {
      case scala if scala.getGroupId == "org.scala-lang" && scala.getArtifactId == "scala-library" =>
        ServiceManager.scalaBinaryVersion(scala.getVersion)
    } getOrElse ServiceManager.DefaultScalaBinaryVersion
  }

  private def devModeDependencies(scalaBinaryVersion: String, artifacts: Seq[String]) = artifacts.map { dep =>
    new Dependency(new DefaultArtifact(s"com.lightbend.lagom:${dep}_$scalaBinaryVersion:${LagomVersion.current}"), "runtime")
  }

  private def calculateDevModeDependencies(scalaBinaryVersion: String, playService: Boolean,
    serviceLocatorUrl: Option[String], cassandraPort: Option[Int]): Seq[Dependency] = {
    if (playService) {
      devModeDependencies(scalaBinaryVersion, Seq("lagom-play-integration", "lagom-reloadable-server"))
    } else {
      devModeDependencies(
        scalaBinaryVersion,
        Seq("lagom-reloadable-server") ++
          serviceLocatorUrl.fold(Seq.empty[String])(_ =>
            Seq("lagom-service-registry-client", "lagom-service-registration"))
      )
    }
  }

  def getPortMap(portRange: PortRangeBean, externalProjects: Seq[String]): Map[ProjectName, Port] = synchronized {
    portMap match {
      case Some(map) => map
      case None =>
        val lagomServices = facade.locateServices
        val map = PortAssigner.computeProjectsPort(
          PortRange(portRange.min, portRange.max),
          lagomServices.map(project => new ProjectName(project.getArtifactId))
            ++ externalProjects.map(ProjectName.apply)
        )
        portMap = Some(map)
        map
    }
  }

  def startServiceDevMode(project: MavenProject, port: Int, serviceLocatorUrl: Option[String],
    cassandraPort: Option[Int], cassandraKeyspace: String, kafkaAddress: String,
    playService: Boolean, additionalWatchDirs: Seq[File]): Unit = synchronized {
    runningServices.get(project) match {
      case Some(service) =>
        logger.info("Service " + project.getArtifactId + " already running!")
      case None =>

        try {
          // First, resolve the project. We need to do this so that we can find out the Scala version.
          val plainDeps = facade.resolveProject(project, Nil)

          val scalaBinaryVersion = detectScalaBinaryVersion(plainDeps.map(_.getArtifact))

          val devDeps = calculateDevModeDependencies(scalaBinaryVersion, playService, serviceLocatorUrl, cassandraPort)

          // Now resolve again with the dev mode dependencies added
          val projectDependencies = resolveDependencies(project, devDeps)

          val projects = project +: projectDependencies.internal

          // This is the list of projects to build on each file change, in build order, calculated by getting the list
          // of all projects that we depend on in build order, and limiting it to just the ones that we've already
          // calculated are a runtime scoped dependency
          val buildProjects = session.getProjectDependencyGraph.getUpstreamProjects(project, true).asScala.collect {
            case runtimeDep if projects.contains(runtimeDep) =>
              // We need to ensure that we resolve each project that we depend on too
              facade.resolveProject(runtimeDep, Nil)
              runtimeDep
          } :+ project

          val sourceDirsToWatch = projects.flatMap { project =>
            new File(project.getBuild.getSourceDirectory) ::
              project.getBuild.getResources.asScala.map(r => new File(r.getDirectory)).toList
          } ++ additionalWatchDirs

          val watchService = FileWatchService.defaultWatchService(
            new File(session.getTopLevelProject.getBuild.getDirectory, "target"),
            200, logger
          )

          val serviceClassPath = projects.map { project =>
            new File(project.getBuild.getOutputDirectory)
          }

          val devSettings = LagomConfig.actorSystemConfig(project.getArtifactId) ++
            serviceLocatorUrl.map(LagomConfig.ServiceLocatorUrl -> _).toMap ++
            cassandraPort.fold(Map.empty[String, String]) { port =>
              // FIXME: The cassandra configuration should be always injected
              // (even when the cassandraEnabled flag is false, as otherwise the 
              // Lagom services won't properly work with a locally running Cassandra
              // instance - see
              // http://www.lagomframework.com/documentation/1.0.x/java/CassandraServer.html#Connecting-to-a-locally-running-Cassandra-instance).
              LagomConfig.cassandraPort(port) ++ LagomConfig.cassandraKeySpace(cassandraKeyspace)
            } ++ Map(LagomConfig.KafkaAddress -> kafkaAddress)

          val scalaClassLoader = scalaClassLoaderManager.extractScalaClassLoader(projectDependencies.external)

          // Because Maven plugins may be run in their own classloaders, we can't use any instance of something that
          // we've created as a mutex, because another project might have loaded us in a different classloader. So
          // while this is rather hacky, it is guaranteed to be a singleton from the perspective of all the instances
          // of our plugin
          val reloadLock = classOf[Maven]

          val service = Reloader.startDevMode(
            scalaClassLoader,
            projectDependencies.external.map(_.getFile),
            () => {
              reloadCompile(buildProjects, serviceClassPath)
            },
            identity,
            sourceDirsToWatch,
            watchService,
            new File(project.getBuild.getDirectory),
            devSettings.toSeq,
            port,
            reloadLock
          )

          // Eagerly reload to start
          service.reload()

          // Setup trigger to reload when a source file changes
          service.addChangeListener(() => service.reload())

          LagomKeys.LagomServiceUrl.put(project, service.url())

          runningServices += (project -> service)
        } catch {
          case NonFatal(e) =>
            throw new RuntimeException(s"Failed to start service ${project.getArtifactId}: ${e.getMessage}", e)
        }
    }
  }

  private def reloadCompile(projects: Seq[MavenProject], serviceClassPath: Seq[File]): CompileResult = {
    try {
      facade.executeLifecyclePhase(projects, "process-classes")
      // Lagom doesn't really use the source map, so we don't worry about calculating it here
      CompileSuccess(Map.empty, serviceClassPath)
    } catch {
      case NonFatal(e) =>
        CompileFailure(new PlayException("Compile failure", "compilation failed", e))
    }

  }

  def stopService(project: MavenProject) = synchronized {
    runningServices.get(project) match {
      case Some(service) => service.close()
      case None => logger.info("Service " + project.getArtifactId + " was not running!")
    }
  }

  def startExternalProject(dependency: Dependency, port: Int, serviceLocatorUrl: Option[String],
    cassandraPort: Option[Int], cassandraKeyspace: String, kafkaAddress: String, playService: Boolean) = synchronized {
    runningExternalProjects.get(dependency) match {
      case Some(service) =>
        logger.info("External project " + dependency.getArtifact.getArtifactId + " already running!")
      case None =>

        // First resolve to find out the scala binary version
        val plainDeps = facade.resolveDependency(dependency, Nil)

        val scalaBinaryVersion = detectScalaBinaryVersion(plainDeps)

        val devDeps = calculateDevModeDependencies(scalaBinaryVersion, playService, serviceLocatorUrl, cassandraPort)

        // Now resolve with the dev mode deps added
        val dependencies = facade.resolveDependency(dependency, devDeps)

        val devSettings = LagomConfig.actorSystemConfig(dependency.getArtifact.getArtifactId) ++
          serviceLocatorUrl.map(LagomConfig.ServiceLocatorUrl -> _).toMap ++
          // FIXME: The cassandra configuration should be always injected
          // (even when the cassandraEnabled flag is false, as otherwise the 
          // Lagom services won't properly work with a locally running Cassandra
          // instance - see
          // http://www.lagomframework.com/documentation/1.0.x/java/CassandraServer.html#Connecting-to-a-locally-running-Cassandra-instance).
          cassandraPort.fold(Map.empty[String, String]) { port =>
            LagomConfig.cassandraPort(port) ++ LagomConfig.cassandraKeySpace(cassandraKeyspace)
          } ++ Map(LagomConfig.KafkaAddress -> kafkaAddress)

        val scalaClassLoader = scalaClassLoaderManager.extractScalaClassLoader(dependencies)

        val service = Reloader.startNoReload(scalaClassLoader, dependencies.map(_.getFile),
          new File(session.getCurrentProject.getBuild.getDirectory), devSettings.toSeq, port)

        runningExternalProjects += (dependency -> service)
    }
  }

  def stopExternalProject(dependency: Dependency) = synchronized {
    runningExternalProjects.get(dependency) match {
      case Some(service) => service.close()
      case None => logger.info("Service " + dependency.getArtifact.getArtifactId + " was not running!")
    }
  }

  private def resolveDependencies(project: MavenProject, additionalDependencies: Seq[Dependency]): ProjectDependencies = {

    val dependencies = facade.resolveProject(project, additionalDependencies)

    val runtimeDependencies = dependencies.filter(d => RuntimeScopes(d.getScope))
    val eitherDepsOrProjects = runtimeDependencies.map { dep =>
      val artifact = dep.getArtifact
      val projectKey = ArtifactUtils.key(artifact.getGroupId, artifact.getArtifactId, artifact.getVersion)
      session.getProjectMap.get(projectKey) match {
        case null => Left(dep)
        case projectDep => Right(projectDep)
      }
    }
    val external = eitherDepsOrProjects.collect { case Left(dep) => dep.getArtifact }
    val internal = eitherDepsOrProjects.collect { case Right(projectDep) => projectDep }

    ProjectDependencies(external, internal)
  }

  private val RuntimeScopes = Set("runtime", "compile", "system")

}

object ServiceManager {
  val DefaultScalaBinaryVersion = "NONE"

  // These regexps are pulled from sbt's CrossVersionUtil
  private val ScalaReleaseVersion = """(\d+\.\d+)\.\d+(?:-\d+)?""".r
  private val ScalaBinCompatVersion = """(\d+\.\d+)\.\d+-bin(?:-.*)?""".r
  private val ScalaNonReleaseVersion = """(\d+\.\d+)\.(\d+)-\w+""".r

  def scalaBinaryVersion(version: String) = {
    version match {
      case ScalaReleaseVersion(binaryVersion) => binaryVersion
      case ScalaBinCompatVersion(binaryVersion) => binaryVersion
      case ScalaNonReleaseVersion(binaryVersion, patch) if patch.toInt > 0 => binaryVersion
      case _ => version
    }
  }
}

private case class ProjectDependencies(external: Seq[Artifact], internal: Seq[MavenProject])
