package com.lightbend.lagom.maven

import javax.inject.Inject

import com.lightbend.lagom.dev.{ Colors, ConsoleHelper, LagomConfig }
import com.lightbend.lagom.dev.PortAssigner.ProjectName
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import play.dev.filewatch.LoggerProxy

import scala.beans.BeanProperty

/**
 * Run a service, blocking until the user hits enter before stopping it again.
 */
class RunMojo @Inject() (mavenFacade: MavenFacade, logger: LoggerProxy, session: MavenSession) extends AbstractMojo {

  val consoleHelper = new ConsoleHelper(new Colors("lagom.noformat"))

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
class StartMojo @Inject() (serviceManager: ServiceManager, session: MavenSession) extends AbstractMojo {

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

  override def execute(): Unit = {

    val project = session.getCurrentProject

    if (!lagomService && !playService) {
      sys.error(s"${project.getArtifactId} is not a Lagom service!")
    }

    val serviceLocatorUrl = (serviceLocatorEnabled, this.serviceLocatorUrl) match {
      case (false, _) => None
      case (true, null) => Some(s"http://localhost:$serviceLocatorPort")
      case (true, configured) => Some(configured)
    }

    val selectedPort = if (servicePort == -1) {
      val portMap = serviceManager.getPortMap(servicePortRange)
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

    serviceManager.startServiceDevMode(project, selectedPort, serviceLocatorUrl, cassandraPort, cassandraKeyspace, playService = playService)
  }
}

/**
 * Stop a service.
 */
class StopMojo @Inject() (serviceManager: ServiceManager, session: MavenSession) extends AbstractMojo {

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

/**
 * Starts all services.
 */
class StartAllMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, session: MavenSession) extends AbstractMojo {

  val consoleHelper = new ConsoleHelper(new Colors("lagom.noformat"))

  override def execute(): Unit = {

    val services = facade.locateServices

    executeGoal("startCassandra")
    executeGoal("startServiceLocator")

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
class StopAllMojo @Inject() (facade: MavenFacade, session: MavenSession) extends AbstractMojo {

  override def execute(): Unit = {
    val services = facade.locateServices

    services.foreach { service =>
      facade.executeMavenPluginGoal(service, "stop")
    }

    executeGoal("stopServiceLocator")
    executeGoal("stopCassandra")
  }

  def executeGoal(name: String) = {
    facade.executeMavenPluginGoal(session.getCurrentProject, name)
  }
}

/**
 * Run a service, blocking until the user hits enter before stopping it again.
 */
class RunAllMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, session: MavenSession) extends AbstractMojo {

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

