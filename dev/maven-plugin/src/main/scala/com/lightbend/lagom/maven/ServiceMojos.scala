/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.maven

import java.io.File
import javax.inject.Inject

import com.lightbend.lagom.dev.{ Colors, ConsoleHelper }
import com.lightbend.lagom.dev.PortAssigner.ProjectName
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Dependency

import scala.beans.BeanProperty
import java.util.{ Collections, List => JList }

import org.apache.maven.RepositoryUtils

import scala.collection.JavaConverters._

/**
 * Run a service, blocking until the user hits enter before stopping it again.
 */
class RunMojo @Inject() (mavenFacade: MavenFacade, logger: MavenLoggerProxy, session: MavenSession) extends LagomAbstractMojo {

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
  var serviceAddress: String = _

  // TODO: deprecate in favor of serviceHttpPort
  @BeanProperty
  var servicePort: Int = _

  @BeanProperty
  var serviceHttpsPort: Int = _

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
      case (false, _)         => None
      case (true, null)       => Some(s"http://localhost:$serviceLocatorPort")
      case (true, configured) => Some(configured)
    }

    def selectPort(servicePort: Int, useTls: Boolean): Int = {
      if (servicePort == -1) {
        val portMap = serviceManager.getPortMap(
          servicePortRange,
          externalProjects.asScala.map(d => d.artifact.getGroupId + ":" + d.artifact.getArtifactId)
        )
        val portName = {
          val pn = ProjectName(project.getArtifactId)
          if (useTls) pn.withTls else pn
        }
        val port = portMap.get(portName)
        port.map(_.value).getOrElse {
          sys.error(s"No port selected for service ${project.getArtifactId} (use TLS: $useTls)")
        }
      } else {
        servicePort
      }
    }

    val selectedPort = selectPort(servicePort, useTls = false)
    val selectedHttpsPort = selectPort(serviceHttpsPort, useTls = true)

    val cassandraPort = if (cassandraEnabled) {
      Some(this.cassandraPort)
    } else None

    serviceManager.startServiceDevMode(project, serviceAddress, selectedPort, selectedHttpsPort, serviceLocatorUrl, cassandraPort, playService = playService, resolvedWatchDirs)
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
  var serviceAddress: String = _

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

  override def execute(): Unit = {

    val serviceLocatorUrl = (serviceLocatorEnabled, this.serviceLocatorUrl) match {
      case (false, _)         => None
      case (true, null)       => Some(s"http://localhost:$serviceLocatorPort")
      case (true, configured) => Some(configured)
    }

    val cassandraPort = if (cassandraEnabled) {
      Some(this.cassandraPort)
    } else None

    lazy val portMap = serviceManager.getPortMap(
      servicePortRange,
      externalProjects.asScala.map(d => d.artifact.getGroupId + ":" + d.artifact.getArtifactId)
    )

    externalProjects.asScala.foreach { project =>
      if (project.artifact == null || project.artifact.getGroupId == null || project.artifact.getArtifactId == null ||
        project.artifact.getVersion == null) {
        sys.error("External projects must specify an artifact with a groupId, artifactId and version")
      }

      def selectPort(servicePort: Int, useTls: Boolean) = {
        if (servicePort == -1) {
          val artifactBasename = project.artifact.getGroupId + ":" + project.artifact.getArtifactId

          val portName = {
            val pn = ProjectName(artifactBasename)
            if (useTls) pn.withTls else pn
          }
          val port = portMap.get(portName)
          port.map(_.value).getOrElse {
            sys.error(s"No port selected for service $artifactBasename (use TLS: $useTls)")
          }
        } else {
          servicePort
        }
      }

      val selectedPort = selectPort(project.servicePort, useTls = false)
      val selectedHttpsPort = selectPort(project.serviceHttpsPort, useTls = false)

      val serviceCassandraPort = cassandraPort.filter(_ => project.cassandraEnabled)

      val dependency = RepositoryUtils.toDependency(project.artifact, session.getRepositorySession.getArtifactTypeRegistry)

      serviceManager.startExternalProject(dependency, serviceAddress, selectedPort, selectedHttpsPort, serviceLocatorUrl, serviceCassandraPort, playService = project.playService)
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

  // TODO: deprecate in favor of serviceHttpPort
  @BeanProperty
  var servicePort: Int = -1

  @BeanProperty
  var serviceHttpsPort: Int = -1

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
