package com.lightbend.lagom.maven

import javax.inject.Inject

import com.lightbend.lagom.core.LagomVersion
import com.lightbend.lagom.dev.Servers
import org.apache.maven.plugin.AbstractMojo
import org.eclipse.aether.artifact.DefaultArtifact
import java.util.{ Collections, List => JList, Map => JMap }

import org.codehaus.plexus.logging.{ Logger, LoggerManager }

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.beans.BeanProperty

class StartCassandraMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, mavenLoggerManager: LoggerManager) extends AbstractMojo {

  @BeanProperty
  var cassandraMaxBootWaitingSeconds: Int = _
  @BeanProperty
  var cassandraPort: Int = _
  @BeanProperty
  var cassandraEnabled: Boolean = _
  @BeanProperty
  var cassandraCleanOnStart: Boolean = _
  @BeanProperty // I'm not sure if it's possible to specify a default value for a literal list in plugin.xml, so specify it here.
  var cassandraJvmOptions: JList[String] = Seq("-Xms256m", "-Xmx1024m", "-Dcassandra.jmx.local.port=4099",
    "-DCassandraLauncher.configResource=dev-embedded-cassandra.yaml").asJava

  override def execute(): Unit = {
    if (cassandraEnabled) {
      // Configure logging to quieten the Cassandra driver
      mavenLoggerManager.getLoggerForComponent("com.datastax").setThreshold(Logger.LEVEL_DISABLED)

      val cp = facade.resolveArtifact(new DefaultArtifact("com.lightbend.lagom", "lagom-cassandra-server_2.11",
        "jar", LagomVersion.current))

      Servers.CassandraServer.start(logger, cp.map(_.getFile), cassandraPort, cassandraCleanOnStart, cassandraJvmOptions.asScala,
        cassandraMaxBootWaitingSeconds.seconds)
    }
  }
}

class StopCassandraMojo @Inject() (logger: MavenLoggerProxy) extends AbstractMojo {
  @BeanProperty
  var cassandraEnabled: Boolean = _

  override def execute(): Unit = {
    if (cassandraEnabled) {
      Servers.tryStop(logger)
    }
  }
}

class StartServiceLocatorMojo @Inject() (logger: MavenLoggerProxy, facade: MavenFacade,
    scalaClassLoaderManager: ScalaClassLoaderManager) extends AbstractMojo {

  @BeanProperty
  var serviceLocatorEnabled: Boolean = _
  @BeanProperty
  var serviceLocatorPort: Int = _
  @BeanProperty
  var serviceGatewayPort: Int = _
  @BeanProperty
  var unmanagedServices: JMap[String, String] = Collections.emptyMap[String, String]

  override def execute(): Unit = {
    if (serviceLocatorEnabled) {
      val cp = facade.resolveArtifact(new DefaultArtifact("com.lightbend.lagom", "lagom-service-locator_2.11",
        "jar", LagomVersion.current))

      val scalaClassLoader = scalaClassLoaderManager.extractScalaClassLoader(cp)

      Servers.ServiceLocator.start(logger, scalaClassLoader, cp.map(_.getFile.toURI.toURL).toArray,
        serviceLocatorPort, serviceGatewayPort, unmanagedServices.asScala.toMap)
    }
  }
}

class StopServiceLocatorMojo @Inject() (logger: MavenLoggerProxy) extends AbstractMojo {

  @BeanProperty
  var serviceLocatorEnabled: Boolean = _

  override def execute(): Unit = {
    if (serviceLocatorEnabled) {
      Servers.ServiceLocator.tryStop(logger)
    }
  }
}