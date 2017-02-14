/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.kafka

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

import org.apache.curator.test.TestingServer
import org.slf4j.LoggerFactory

import com.lightbend.lagom.internal.util.PropertiesLoader

import javax.management.InstanceNotFoundException
import kafka.server.KafkaServerStartable

import scala.collection.JavaConverters._
import java.util.Comparator

class KafkaLocalServer private (kafkaProperties: Properties, zooKeeperServer: KafkaLocalServer.ZooKeperLocalServer) {

  private val kafkaServerRef = new AtomicReference[KafkaServerStartable](null)

  def start(): Unit = {
    if (kafkaServerRef.get == null) {
      // There is a possible race condition here. However, instead of attempting to avoid it 
      // by using a lock, we are working with it and do the necessary clean up if indeed we 
      // end up creating two Kafka server instances.
      val newKafkaServer = KafkaServerStartable.fromProps(kafkaProperties)
      if (kafkaServerRef.compareAndSet(null, newKafkaServer)) {
        zooKeeperServer.start()
        val kafkaServer = kafkaServerRef.get()
        kafkaServer.startup()
      } else newKafkaServer.shutdown()
    }
    // else it's already running
  }

  // this exists only for testing purposes
  private[lagom] def restart(): Unit = {
    val kafkaServer = kafkaServerRef.get()
    if (kafkaServer != null) {
      kafkaServer.shutdown()
      kafkaServer.startup()
    }
  }

  def stop(): Unit = {
    val kafkaServer = kafkaServerRef.getAndSet(null)
    if (kafkaServer != null) {
      try kafkaServer.shutdown()
      catch {
        case t: Throwable => ()
      }
      try zooKeeperServer.stop()
      catch {
        case e: InstanceNotFoundException => () // swallow, see https://github.com/Netflix/curator/issues/121 for why it's ok to do so
      }
    }
    // else it's already stopped
  }

}

object KafkaLocalServer {
  final val DefaultPort = 9092
  final val DefaultPropertiesFile = "/kafka-server.properties"
  final val DefaultResetOnStart = true

  private final val KafkaDataFolderName = "kafka_data"

  private val Log = LoggerFactory.getLogger(classOf[KafkaLocalServer])

  private lazy val tempDir = System.getProperty("java.io.tmpdir")

  def apply(cleanOnStart: Boolean): KafkaLocalServer = this(DefaultPort, ZooKeperLocalServer.DefaultPort, DefaultPropertiesFile, Some(tempDir), cleanOnStart)

  def apply(kafkaPort: Int, zooKeperServerPort: Int, kafkaPropertiesFile: String, targetDir: Option[String], cleanOnStart: Boolean): KafkaLocalServer = {
    val kafkaDataDir = dataDirectory(targetDir, KafkaDataFolderName)
    Log.info(s"Kafka data directory is $kafkaDataDir.")

    val kafkaProperties = createKafkaProperties(kafkaPropertiesFile, kafkaPort, zooKeperServerPort, kafkaDataDir)

    if (cleanOnStart) deleteDirectory(kafkaDataDir)

    new KafkaLocalServer(kafkaProperties, new ZooKeperLocalServer(zooKeperServerPort, cleanOnStart, targetDir))
  }

  /**
   * Creates a Properties instance for Kafka customized with values passed in argument.
   */
  private def createKafkaProperties(kafkaPropertiesFile: String, kafkaPort: Int, zooKeperServerPort: Int, dataDir: File): Properties = {
    val kafkaProperties = PropertiesLoader.from(kafkaPropertiesFile)
    kafkaProperties.setProperty("log.dirs", dataDir.getAbsolutePath)
    kafkaProperties.setProperty("listeners", s"PLAINTEXT://:$kafkaPort")
    kafkaProperties.setProperty("zookeeper.connect", s"localhost:$zooKeperServerPort")
    kafkaProperties
  }

  private def deleteDirectory(directory: File): Unit = {
    if (directory.exists()) try {
      val rootPath = Paths.get(directory.getAbsolutePath)

      val files = Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).iterator().asScala
      files.foreach(Files.delete)
      Log.debug(s"Deleted ${directory.getAbsolutePath}.")
    } catch {
      case e: Exception => Log.warn(s"Failed to delete directory ${directory.getAbsolutePath}.", e)
    }
  }

  /**
   * If the passed `baseDirPath` points to an existing directory for which the application has write access,
   * return a File instance that points to `baseDirPath/directoryName`. Otherwise, return a File instance that
   * points to `tempDir/directoryName` where `tempDir` is the system temporary folder returned by the system
   * property "java.io.tmpdir".
   *
   * @param baseDirPath The path to the base directory.
   * @param directoryName The name to use for the child folder in the base directory.
   * @throws IllegalArgumentException If the passed `directoryName` is not a valid directory name.
   * @return A file directory that points to either `baseDirPath/directoryName` or `tempDir/directoryName`.
   */
  private def dataDirectory(baseDirPath: Option[String], directoryName: String): File = {
    lazy val tempDirMessage = s"Will attempt to create folder $directoryName in the system temporary directory: $tempDir"

    val maybeBaseDir = baseDirPath.map(new File(_)).filter(f => f.exists())

    val baseDir = {
      maybeBaseDir match {
        case None =>
          Log.warn(s"Directory $baseDirPath doesn't exist. $tempDirMessage.")
          new File(tempDir)
        case Some(directory) =>
          if (!directory.isDirectory()) {
            Log.warn(s"$baseDirPath is not a directory. $tempDirMessage.")
            new File(tempDir)
          } else if (!directory.canWrite()) {
            Log.warn(s"The application does not have write access to directory $baseDirPath. $tempDirMessage.")
            new File(tempDir)
          } else directory
      }
    }

    val dataDirectory = new File(baseDir, directoryName)
    if (dataDirectory.exists() && !dataDirectory.isDirectory())
      throw new IllegalArgumentException(s"Cannot use $directoryName as a directory name because a file with that name already exists in $dataDirectory.")

    dataDirectory
  }

  private class ZooKeperLocalServer(port: Int, cleanOnStart: Boolean, targetDir: Option[String]) {
    private val zooKeeperServerRef = new AtomicReference[TestingServer](null)

    def start(): Unit = {
      val zookeeperDataDir = dataDirectory(targetDir, ZooKeperLocalServer.ZookeeperDataFolderName)
      if (zooKeeperServerRef.compareAndSet(null, new TestingServer(port, zookeeperDataDir, /*start=*/ false))) {
        Log.info(s"Zookeeper data directory is $zookeeperDataDir.")

        if (cleanOnStart) deleteDirectory(zookeeperDataDir)

        val zooKeeperServer = zooKeeperServerRef.get
        zooKeeperServer.start() // blocking operation
      }
      // else it's already running
    }

    def stop(): Unit = {
      val zooKeeperServer = zooKeeperServerRef.getAndSet(null)
      if (zooKeeperServer != null)
        try zooKeeperServer.stop()
        catch {
          case e: IOException => () // nothing to do if an exception is thrown while shutting down
        }
      // else it's already stopped
    }
  }

  object ZooKeperLocalServer {
    private[kafka] final val DefaultPort = 2181
    private final val ZookeeperDataFolderName = "zookeeper_data"
  }
}
