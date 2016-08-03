package com.lightbend.lagom.maven

import java.io.{ Closeable, File }
import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.core.LagomVersion
import com.lightbend.lagom.dev.PortAssigner.{ Port, PortRange, ProjectName }
import com.lightbend.lagom.dev.Reloader.{ CompileFailure, CompileResult, CompileSuccess }
import com.lightbend.lagom.dev.{ LagomConfig, PortAssigner, Reloader }
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

  private var runningServices = Map.empty[MavenProject, Closeable]
  private var portMap: Option[Map[ProjectName, Port]] = None

  // todo detect and inject scala binary version
  private def devModeDependencies(artifacts: Seq[String]) = artifacts.map { dep =>
    new Dependency(new DefaultArtifact(s"com.lightbend.lagom:${dep}_2.11:${LagomVersion.current}"), "runtime")
  }

  def getPortMap(portRange: PortRangeBean): Map[ProjectName, Port] = synchronized {
    portMap match {
      case Some(map) => map
      case None =>
        val lagomServices = facade.locateServices
        val map = PortAssigner.computeProjectsPort(
          PortRange(portRange.min, portRange.max),
          lagomServices.map(project => new ProjectName(project.getArtifactId))
        )
        portMap = Some(map)
        map
    }
  }

  def startServiceDevMode(project: MavenProject, port: Int, serviceLocatorUrl: Option[String],
    cassandraPort: Option[Int], cassandraKeyspace: String, playService: Boolean): Unit = synchronized {
    runningServices.get(project) match {
      case Some(service) =>
        logger.info("Service " + project.getArtifactId + " already running!")
      case None =>

        try {
          val devDeps = if (playService) {
            devModeDependencies(Seq("lagom-play-integration", "lagom-reloadable-server"))
          } else {
            devModeDependencies(
              Seq("lagom-reloadable-server") ++
                serviceLocatorUrl.fold(Seq.empty[String])(_ =>
                  Seq("lagom-service-registry-client", "lagom-service-registration") ++
                    cassandraPort.fold(Seq.empty[String])(_ => Seq("lagom-cassandra-registration")))
            )
          }

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
          }

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
              LagomConfig.cassandraPort(port) ++ LagomConfig.cassandraKeySpace(cassandraKeyspace)
            }

          val scalaClassLoader = scalaClassLoaderManager.extractScalaClassLoader(projectDependencies.external)

          val service = Reloader.startDevMode(
            scalaClassLoader,
            projectDependencies.external.map(_.getFile),
            () => {
              reloadCompile(buildProjects, serviceClassPath)
            },
            identity,
            // todo make configurable
            sourceDirsToWatch,
            watchService,
            new File(project.getBuild.getDirectory),
            devSettings.toSeq,
            port
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
      // todo calculate source map
      CompileSuccess(Map.empty, serviceClassPath)
    } catch {
      case NonFatal(e) =>
        // todo populate properly
        CompileFailure(new PlayException("Compile failure", "compilation failed", e))
    }

  }

  def stopService(project: MavenProject) = {
    runningServices.get(project) match {
      case Some(service) => service.close()
      case None => logger.info("Service " + project.getArtifactId + " was not running!")
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
        case project => Right(project)
      }
    }
    val external = eitherDepsOrProjects.collect { case Left(dep) => dep.getArtifact }
    val internal = eitherDepsOrProjects.collect { case Right(project) => project }

    ProjectDependencies(external, internal)
  }

  private val RuntimeScopes = Set("runtime", "compile", "system")

}

private case class ProjectDependencies(external: Seq[Artifact], internal: Seq[MavenProject])
