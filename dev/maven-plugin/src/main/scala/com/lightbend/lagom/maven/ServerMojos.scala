/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import java.io.File
import javax.inject.Inject

import com.lightbend.lagom.core.LagomVersion
import com.lightbend.lagom.dev.{ Servers, StaticServiceLocations }
import org.eclipse.aether.artifact.DefaultArtifact
import java.util.{ Collections, List => JList, Map => JMap }

import org.codehaus.plexus.logging.{ Logger, LoggerManager }

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.beans.BeanProperty
import org.apache.maven.execution.MavenSession
import org.eclipse.aether.graph.Dependency

class StartCassandraMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, mavenLoggerManager: LoggerManager,
                                    scalaClassLoaderManager: ScalaClassLoaderManager) extends LagomAbstractMojo {

  @BeanProperty
  var cassandraMaxBootWaitingSeconds: Int = _
  @BeanProperty
  var cassandraPort: Int = _
  @BeanProperty
  var cassandraEnabled: Boolean = _
  @BeanProperty
  var cassandraCleanOnStart: Boolean = _
  @BeanProperty // I'm not sure if it's possible to specify a default value for a literal list in plugin.xml, so specify it here.
  var cassandraJvmOptions: JList[String] = Seq("-Xms256m", "-Xmx1024m", "-Dcassandra.jmx.local.port=4099").asJava

  override def execute(): Unit = {
    if (cassandraEnabled) {
      // Configure logging to quieten the Cassandra driver
      mavenLoggerManager.getLoggerForComponent("com.datastax").setThreshold(Logger.LEVEL_DISABLED)

      val cp = facade.resolveArtifact(new DefaultArtifact("com.lightbend.lagom", "lagom-cassandra-server_2.11",
        "jar", LagomVersion.current))

      val scalaClassLoader = scalaClassLoaderManager.extractScalaClassLoader(cp)

      Servers.CassandraServer.start(logger, scalaClassLoader, cp.map(_.getFile), cassandraPort, cassandraCleanOnStart,
        cassandraJvmOptions.asScala, cassandraMaxBootWaitingSeconds.seconds)
    }
  }
}

class StopCassandraMojo @Inject() (logger: MavenLoggerProxy) extends LagomAbstractMojo {
  @BeanProperty
  var cassandraEnabled: Boolean = _

  override def execute(): Unit = {
    if (cassandraEnabled) {
      Servers.tryStop(logger)
    }
  }
}

class StartKafkaMojo @Inject() (facade: MavenFacade, logger: MavenLoggerProxy, mavenLoggerManager: LoggerManager, session: MavenSession) extends LagomAbstractMojo {

  @BeanProperty
  var kafkaPort: Int = _
  @BeanProperty
  var zookeeperPort: Int = _
  @BeanProperty
  var kafkaEnabled: Boolean = _
  @BeanProperty
  var kafkaCleanOnStart: Boolean = _
  @BeanProperty
  var kafkaPropertiesFile: File = _
  @BeanProperty // I'm not sure if it's possible to specify a default value for a literal list in plugin.xml, so specify it here.
  var kafkaJvmOptions: JList[String] = Seq("-Xms256m", "-Xmx1024m").asJava

  override def execute(): Unit = {

    if (kafkaEnabled) {

      val dependency = {
        val artifact = new DefaultArtifact("com.lightbend.lagom", "lagom-kafka-server_2.11",
          "jar", LagomVersion.current)
        new Dependency(artifact, "runtime")
      }
      val cp = facade.resolveArtifact(dependency.getArtifact)

      logger.debug {
        val text = {
          cp.map { artifact =>
            val groupId = artifact.getGroupId
            val artifactId = artifact.getArtifactId
            val version = artifact.getVersion
            s"$groupId $artifactId $version"
          }.mkString("\t", "\n\t", "")
        }
        "Classpath used to start Kafka:\n" + text
      }

      val / = java.io.File.separator
      val project = session.getTopLevelProject
      val projectTargetDir = project.getBuild.getDirectory // path is absolute
      // target directory matches the one used in the Lagom sbt plugin
      val targetDir = new java.io.File(projectTargetDir + / + "lagom-dynamic-projects" + / + "lagom-internal-meta-project-kafka" + / + "target")
      // properties file doesn't need to be provided by users, in which case the default one included with Lagom will be used
      val kafkaPropertiesFile = Option(this.kafkaPropertiesFile)

      Servers.KafkaServer.start(logger, cp.map(_.getFile), kafkaPort, zookeeperPort, kafkaPropertiesFile, kafkaJvmOptions.asScala, targetDir, kafkaCleanOnStart)
    }
  }
}

class StopKafkaMojo @Inject() (logger: MavenLoggerProxy) extends LagomAbstractMojo {
  @BeanProperty
  var kafkaEnabled: Boolean = _

  override def execute(): Unit = {
    if (kafkaEnabled) {
      Servers.tryStop(logger)
    }
  }
}

class StartServiceLocatorMojo @Inject() (logger: MavenLoggerProxy, facade: MavenFacade,
                                         scalaClassLoaderManager: ScalaClassLoaderManager) extends LagomAbstractMojo {

  @BeanProperty
  var serviceLocatorEnabled: Boolean = _
  @BeanProperty
  var serviceLocatorPort: Int = _
  @BeanProperty
  var serviceGatewayPort: Int = _
  @BeanProperty
  var serviceGatewayImpl: String = _
  @BeanProperty
  var unmanagedServices: JMap[String, String] = Collections.emptyMap[String, String]

  @BeanProperty
  var cassandraPort: Int = _
  @BeanProperty
  var cassandraEnabled: Boolean = _
  @BeanProperty
  var kafkaPort: Int = _
  @BeanProperty
  var kafkaAddress: String = _
  @BeanProperty
  var kafkaEnabled: Boolean = _

  override def execute(): Unit = {

    if (serviceLocatorEnabled) {
      val cp = facade.resolveArtifact(new DefaultArtifact("com.lightbend.lagom", "lagom-service-locator_2.11",
        "jar", LagomVersion.current))

      val scalaClassLoader = scalaClassLoaderManager.extractScalaClassLoader(cp)

      val theKafkaAddress = if (kafkaAddress == null) s"127.0.0.1:$kafkaPort" else kafkaAddress

      val scalaUnmanagedServices = StaticServiceLocations.staticServiceLocations(cassandraPort, theKafkaAddress) ++
        unmanagedServices.asScala.toMap

      Servers.ServiceLocator.start(logger, scalaClassLoader, cp.map(_.getFile.toURI.toURL).toArray,
        serviceLocatorPort, serviceGatewayPort, scalaUnmanagedServices, serviceGatewayImpl)
    }
  }
}

class StopServiceLocatorMojo @Inject() (logger: MavenLoggerProxy) extends LagomAbstractMojo {

  @BeanProperty
  var serviceLocatorEnabled: Boolean = _

  override def execute(): Unit = {
    if (serviceLocatorEnabled) {
      Servers.ServiceLocator.tryStop(logger)
    }
  }
}
