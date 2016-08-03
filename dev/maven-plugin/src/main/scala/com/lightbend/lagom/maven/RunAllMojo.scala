package com.lightbend.lagom.maven

import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.dev.{ Colors, ConsoleHelper }
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo

/**
 * Maven MOJO for implementing runAll.
 */
@Singleton
class RunAllMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, session: MavenSession, serviceManager: ServiceManager) extends AbstractMojo {

  val consoleHelper = new ConsoleHelper(new Colors("lagom.noformat"))

  override def execute(): Unit = {

    val services = facade.locateServices

    executeGoal("startCassandra")
    executeGoal("startServiceLocator")

    val serviceUrls = services.map { project =>
      facade.executeMavenPluginGoal(project, "run")
      project.getArtifactId -> LagomKeys.LagomServiceUrl.get(project).getOrElse {
        sys.error(s"Service ${project.getArtifactId} is not running?")
      }
    }

    consoleHelper.printStartScreen(logger, serviceUrls)

    consoleHelper.blockUntilExit()

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
