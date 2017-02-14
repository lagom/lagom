/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import java.io.File
import javax.inject.Inject

import com.lightbend.lagom.dev.{ Colors, ConsoleHelper, LagomConfig }
import com.lightbend.lagom.dev.PortAssigner.ProjectName
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Dependency
import play.dev.filewatch.LoggerProxy

import scala.beans.BeanProperty
import java.util.{ Collections, List => JList }

import org.apache.maven.RepositoryUtils

import scala.collection.JavaConverters._

/**
 * Run a service, blocking until the user hits enter before stopping it again.
 */
class RunMojo @Inject() (mavenFacade: MavenFacade, logger: LoggerProxy, session: MavenSession) extends LagomAbstractMojo {

  private val consoleHelper = new ConsoleHelper(new Colors("lagom.noformat"))

  override def execute(): Unit = {
    val project = session.getCurrentProject
    mavenFacade.executeMavenPluginGoal(project, "start")

    val serviceUrl = LagomKeys.LagomServiceUrl.get(project).getOrElse {
      sys.error(s"Service ${project.getArtifactId} is not running?")
    }

    consoleHelper.printStartScreen(logger, Seq(project.getArtifactId -> serviceUrl))

    consoleHelper.blockUntilExit()

    mavenFacade.executeMavenPluginGoal(project, "stop")
  }
}

/**
 * Start a service.
 */
class StartMojo @Inject() (serviceManager: ServiceManager, session: MavenSession) extends LagomAbstractMojo {

  @BeanProperty
  var lagomService: Boolean = _

  @BeanProperty
  var playService: Boolean = _

  @BeanProperty
  var servicePort: Int = _

  @BeanProperty
  var servicePortRange: PortRangeBean = new PortRangeBean

  @BeanProperty
  var serviceLocatorPort: Int = _

  @BeanProperty
  var serviceLocatorEnabled: Boolean = _

  @BeanProperty
  var serviceLocatorUrl: String = _

  @BeanProperty
  var cassandraEnabled: Boolean = _

  @BeanProperty
  var cassandraPort: Int = _

  @BeanProperty
  var kafkaPort: Int = _

  @BeanProperty
  var kafkaAddress: String = _

  @BeanProperty
  var externalProjects: JList[ExternalProject] = Collections.emptyList()

  @BeanProperty
  var watchDirs: JList[String] = Collections.emptyList()

  override def execute(): Unit = {

    val project = session.getCurrentProject

    val resolvedWatchDirs = watchDirs.asScala.map { dir =>
      val file = new File(dir)
      if (!file.isAbsolute) {
        new File(project.getBasedir, dir)
      } else file
    }

    if (!lagomService && !playService) {
      sys.error(s"${project.getArtifactId} is not a Lagom service!")
    }

    val serviceLocatorUrl = (serviceLocatorEnabled, this.serviceLocatorUrl) match {
      case (false, _) => None
      case (true, null) => Some(s"http://localhost:$serviceLocatorPort")
      case (true, configured) => Some(configured)
    }

    val selectedPort = if (servicePort == -1) {
      val portMap = serviceManager.getPortMap(
        servicePortRange,
        externalProjects.asScala.map(d => d.artifact.getGroupId + ":" + d.artifact.getArtifactId)
      )
      val port = portMap.get(ProjectName(project.getArtifactId))
      port.map(_.value).getOrElse {
        sys.error("No port selected for service " + project.getArtifactId)
      }
    } else {
      servicePort
    }

    val cassandraPort = if (cassandraEnabled) {
      Some(this.cassandraPort)
    } else None

    val cassandraKeyspace = LagomConfig.normalizeCassandraKeyspaceName(project.getArtifactId)

    val kafkaAddress = if (this.kafkaAddress == null) s"localhost:${this.kafkaPort}" else this.kafkaAddress

    serviceManager.startServiceDevMode(project, selectedPort, serviceLocatorUrl, cassandraPort, cassandraKeyspace,
      kafkaAddress, playService = playService, resolvedWatchDirs)
  }
}

/**
 * Stop a service.
 */
class StopMojo @Inject() (serviceManager: ServiceManager, session: MavenSession) extends LagomAbstractMojo {

  @BeanProperty
  var lagomService: Boolean = _

  @BeanProperty
  var playService: Boolean = _

  override def execute(): Unit = {
    val project = session.getCurrentProject

    if (!lagomService && !playService) {
      sys.error(s"${project.getArtifactId} is not a Lagom service!")
    }

    serviceManager.stopService(project)
  }
}

class StartExternalProjects @Inject() (serviceManager: ServiceManager, session: MavenSession) extends LagomAbstractMojo {

  @BeanProperty
  var externalProjects: JList[ExternalProject] = Collections.emptyList()

  @BeanProperty
  var servicePortRange: PortRangeBean = new PortRangeBean

  @BeanProperty
  var serviceLocatorPort: Int = _

  @BeanProperty
  var serviceLocatorEnabled: Boolean = _

  @BeanProperty
  var serviceLocatorUrl: String = _

  @BeanProperty
  var cassandraEnabled: Boolean = _

  @BeanProperty
  var cassandraPort: Int = _

  @BeanProperty
  var kafkaPort: Int = _

  @BeanProperty
  var kafkaAddress: String = _

  override def execute(): Unit = {

    val serviceLocatorUrl = (serviceLocatorEnabled, this.serviceLocatorUrl) match {
      case (false, _) => None
      case (true, null) => Some(s"http://localhost:$serviceLocatorPort")
      case (true, configured) => Some(configured)
    }

    val cassandraPort = if (cassandraEnabled) {
      Some(this.cassandraPort)
    } else None

    val kafkaAddress = if (this.kafkaAddress == null) s"localhost:${this.kafkaPort}" else this.kafkaAddress

    lazy val portMap = serviceManager.getPortMap(
      servicePortRange,
      externalProjects.asScala.map(d => d.artifact.getGroupId + ":" + d.artifact.getArtifactId)
    )

    externalProjects.asScala.foreach { project =>
      if (project.artifact == null || project.artifact.getGroupId == null || project.artifact.getArtifactId == null ||
        project.artifact.getVersion == null) {
        sys.error("External projects must specify an artifact with a groupId, artifactId and version")
      }

      val selectedPort = if (project.servicePort == -1) {
        val port = portMap.get(ProjectName(project.artifact.getGroupId + ":" + project.artifact.getArtifactId))
        port.map(_.value).getOrElse {
          sys.error("No port selected for service " + project.artifact.getArtifactId)
        }
      } else {
        project.servicePort
      }

      val serviceCassandraPort = cassandraPort.filter(_ => project.cassandraEnabled)

      val cassandraKeyspace = LagomConfig.normalizeCassandraKeyspaceName(project.artifact.getArtifactId)

      val dependency = RepositoryUtils.toDependency(project.artifact, session.getRepositorySession.getArtifactTypeRegistry)

      serviceManager.startExternalProject(dependency, selectedPort, serviceLocatorUrl, serviceCassandraPort, cassandraKeyspace,
        kafkaAddress, playService = project.playService)
    }
  }

}

class StopExternalProjects @Inject() (serviceManager: ServiceManager, session: MavenSession) extends LagomAbstractMojo {

  @BeanProperty
  var externalProjects: JList[ExternalProject] = Collections.emptyList()

  override def execute(): Unit = {
    externalProjects.asScala.foreach { project =>
      val dependency = RepositoryUtils.toDependency(project.artifact, session.getRepositorySession.getArtifactTypeRegistry)
      serviceManager.stopExternalProject(dependency)
    }
  }
}

class ExternalProject {
  @BeanProperty
  var artifact: Dependency = _

  @BeanProperty
  var playService: Boolean = false

  @BeanProperty
  var servicePort: Int = -1

  @BeanProperty
  var cassandraEnabled: Boolean = true
}

/**
 * Starts all services.
 */
class StartAllMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, session: MavenSession) extends LagomAbstractMojo {

  private val consoleHelper = new ConsoleHelper(new Colors("lagom.noformat"))

  override def execute(): Unit = {

    val services = facade.locateServices

    executeGoal("startKafka")
    executeGoal("startCassandra")
    executeGoal("startServiceLocator")
    executeGoal("startExternalProjects")

    services.foreach { project =>
      facade.executeMavenPluginGoal(project, "start")
    }
  }

  def executeGoal(name: String) = {
    facade.executeMavenPluginGoal(session.getCurrentProject, name)
  }
}

/**
 * Stops all services.
 */
class StopAllMojo @Inject() (facade: MavenFacade, session: MavenSession) extends LagomAbstractMojo {

  @BeanProperty
  var externalProjects: JList[Dependency] = Collections.emptyList()

  override def execute(): Unit = {
    val services = facade.locateServices

    services.foreach { service =>
      facade.executeMavenPluginGoal(service, "stop")
    }

    executeGoal("stopExternalProjects")
    executeGoal("stopServiceLocator")
    executeGoal("stopCassandra")
    executeGoal("stopKafka")
  }

  def executeGoal(name: String) = {
    facade.executeMavenPluginGoal(session.getCurrentProject, name)
  }
}

/**
 * Run a service, blocking until the user hits enter before stopping it again.
 */
class RunAllMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, session: MavenSession) extends LagomAbstractMojo {

  val consoleHelper = new ConsoleHelper(new Colors("lagom.noformat"))

  override def execute(): Unit = {

    val services = facade.locateServices

    executeGoal("startAll")

    val serviceUrls = services.map { project =>
      project.getArtifactId -> LagomKeys.LagomServiceUrl.get(project).getOrElse {
        sys.error(s"Service ${project.getArtifactId} is not running?")
      }
    }

    consoleHelper.printStartScreen(logger, serviceUrls)

    consoleHelper.blockUntilExit()

    executeGoal("stopAll")
  }

  def executeGoal(name: String) = {
    facade.executeMavenPluginGoal(session.getCurrentProject, name)
  }
}
