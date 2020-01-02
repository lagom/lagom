/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cassandra

import java.io._
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import scala.util.control.NonFatal
import scala.concurrent.duration._
import akka.persistence.cassandra.testkit.{ CassandraLauncher => AkkaCassandraLauncher }

/**
 * Starts Cassandra.
 *
 * Adapted from akka.persistence.cassandra.testkit.CassandraLauncher.
 */
class CassandraLauncher {

  private val ForcedShutdownTimeout              = 20.seconds
  private var cassandraDaemon: Option[Closeable] = None

  private val devEmbeddedYaml = "dev-embedded-cassandra.yaml"

  var port: Int = 0

  def address = s"$hostname:$port"

  def hostname = "127.0.0.1"

  def start(
      cassandraDirectory: File,
      yamlConfig: File,
      clean: Boolean,
      port: Int,
      jvmOptions: Array[String]
  ): Unit = this.synchronized {

    if (cassandraDaemon.isEmpty) {

      prepareCassandraDirectory(cassandraDirectory, clean)

      val storagePort = AkkaCassandraLauncher.freePort()
      // NOTE: yamlConfig will be null when not explicitly configured by user
      // we don't use an Option for it because this class will be dynamically loaded
      // and called using structural typing (reflection) by sbt thus on a classloader with scala 2.10
      val fileOrResource = Either.cond(yamlConfig == null, devEmbeddedYaml, yamlConfig)
      // http://wiki.apache.org/cassandra/StorageConfiguration
      val conf = readResource(fileOrResource)
      val amendedConf = conf
        .replace("$PORT", port.toString)
        .replace("$STORAGE_PORT", storagePort.toString)
        .replace("$DIR", cassandraDirectory.getAbsolutePath)

      // write yaml file in cassandra dir (target/embedded-cassandra)
      val devConfigFile = new File(cassandraDirectory, devEmbeddedYaml)
      writeToFile(devConfigFile, amendedConf)

      // Extract the cassandra bundle to the directory
      val cassandraBundleFile = new File(cassandraDirectory, "cassandra-bundle.jar")
      if (!cassandraBundleFile.exists()) {
        val is =
          this.getClass.getClassLoader.getResourceAsStream("akka/persistence/cassandra/launcher/cassandra-bundle.jar")
        try {
          Files.copy(is, cassandraBundleFile.toPath)
        } finally {
          is.close()
        }
      }

      startForked(devConfigFile, cassandraBundleFile, port, jvmOptions)

      this.port = port
    }
  }

  private def prepareCassandraDirectory(cassandraDirectory: File, clean: Boolean): Unit = {
    if (clean) {
      try {
        deleteRecursive(cassandraDirectory)
      } catch {
        case NonFatal(e) => throw new AkkaCassandraLauncher.CleanFailedException(e.getMessage, e)
      }
    }

    if (!cassandraDirectory.exists)
      require(cassandraDirectory.mkdirs(), s"Couldn't create Cassandra directory [$cassandraDirectory]")
  }

  private def startForked(configFile: File, cassandraBundle: File, port: Int, jvmOptions: Array[String]): Unit = {
    // Calculate classpath
    val /         = File.separator
    val javaBin   = s"${System.getProperty("java.home")}${/}bin${/}java"
    val className = "org.apache.cassandra.service.CassandraDaemon"

    val args = Seq(javaBin) ++ jvmOptions ++ Seq(
      "-cp",
      cassandraBundle.getAbsolutePath,
      "-Dcassandra.config=file:" + configFile.getAbsoluteFile,
      "-Dcassandra-foreground=true",
      className
    )

    val builder = new ProcessBuilder(args: _*)
      .inheritIO()

    val process = builder.start()

    val shutdownHook = new Thread {
      override def run(): Unit = {
        process.destroyForcibly()
      }
    }
    Runtime.getRuntime.addShutdownHook(shutdownHook)

    cassandraDaemon = Some(new Closeable {
      override def close(): Unit = {
        process.destroy()
        Runtime.getRuntime.removeShutdownHook(shutdownHook)
        if (process.waitFor(ForcedShutdownTimeout.toMillis, TimeUnit.MILLISECONDS)) {
          val exitStatus = process.exitValue()
          // Java processes killed with SIGTERM may exit with a status of 143
          if (exitStatus != 0 && exitStatus != 143) {
            sys.error(s"Cassandra exited with non zero status: ${process.exitValue()}")
          }
        } else {
          process.destroyForcibly()
          sys.error(s"Cassandra process did not stop within $ForcedShutdownTimeout, killing.")
        }
      }
    })
  }

  /**
   * Stops Cassandra. However, it will not be possible to start Cassandra
   * again in same JVM.
   */
  def stop(): Unit = this.synchronized {
    cassandraDaemon.foreach(_.close())
    cassandraDaemon = None
  }

  private def readResource(fileOrResource: Either[File, String]): String = {
    val sb = new StringBuilder

    val is =
      fileOrResource match {
        case Left(file) =>
          require(file.isFile, s"file [$file] doesn't exist")
          new FileInputStream(file)

        case Right(resource) =>
          val is = getClass.getResourceAsStream("/" + resource)
          require(is != null, s"resource [$resource] doesn't exist")
          is
      }

    val reader = new BufferedReader(new InputStreamReader(is))
    try {
      var line = reader.readLine()
      while (line != null) {
        sb.append(line).append('\n')
        line = reader.readLine()
      }
    } finally {
      reader.close()
    }
    sb.toString
  }

  private def writeToFile(file: File, content: String): Unit = {
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"))
    try {
      writer.write(content)
    } finally {
      writer.close()
    }
  }

  private def deleteRecursive(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursive)
    }
    file.delete()
  }
}
