package com.lightbend.lagom.maven

import javax.inject.Inject

import com.lightbend.lagom.dev.LagomConfig
import com.lightbend.lagom.dev.PortAssigner.ProjectName
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo

import scala.beans.BeanProperty

class RunMojo @Inject() (serviceManager: ServiceManager, session: MavenSession) extends AbstractMojo {
  @BeanProperty
  var lagomService: Boolean = _

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

    if (!lagomService) {
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

    serviceManager.startServiceDevMode(project, selectedPort, serviceLocatorUrl, cassandraPort, cassandraKeyspace)
  }
}

class StopMojo @Inject() (serviceManager: ServiceManager, session: MavenSession) extends AbstractMojo {

  @BeanProperty
  var lagomService: Boolean = _

  override def execute(): Unit = {
    val project = session.getCurrentProject

    if (!lagomService) {
      sys.error(s"${project.getArtifactId} is not a Lagom service!")
    }

    serviceManager.stopService(project)
  }
}