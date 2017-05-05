/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
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

  private val ForcedShutdownTimeout = 20.seconds
  private var cassandraDaemon: Option[Closeable] = None

  var port: Int = 0
  def address = s"$hostname:$port"
  def hostname = "127.0.0.1"

  def start(cassandraDirectory: File, configResource: String, clean: Boolean, port: Int, jvmOptions: Array[String]): Unit = this.synchronized {
    if (cassandraDaemon.isEmpty) {
      prepareCassandraDirectory(cassandraDirectory, clean)

      val storagePort = AkkaCassandraLauncher.freePort()

      // http://wiki.apache.org/cassandra/StorageConfiguration
      val conf = readResource(configResource)
      val amendedConf = conf
        .replace("$PORT", port.toString)
        .replace("$STORAGE_PORT", storagePort.toString)
        .replace("$DIR", cassandraDirectory.getAbsolutePath)
      val configFile = new File(cassandraDirectory, configResource)
      writeToFile(configFile, amendedConf)

      // Extract the cassandra bundle to the directory
      val cassandraBundleFile = new File(cassandraDirectory, "cassandra-bundle.jar")
      if (!cassandraBundleFile.exists()) {
        val is = this.getClass.getClassLoader.getResourceAsStream("akka/persistence/cassandra/launcher/cassandra-bundle.jar")
        try {
          Files.copy(is, cassandraBundleFile.toPath)
        } finally {
          is.close()
        }
      }

      startForked(configFile, cassandraBundleFile, port, jvmOptions)

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
    val / = File.separator
    val javaBin = s"${System.getProperty("java.home")}${/}bin${/}java"
    val className = "org.apache.cassandra.service.CassandraDaemon"
    // Ensure that the directory that logback.xml lives is first in the classpath
    val classpathArgument = (AkkaCassandraLauncher.classpathForResources("logback.xml") :+ cassandraBundle.getAbsolutePath)
      .mkString(File.pathSeparator)

    val args = Seq(javaBin) ++ jvmOptions ++ Seq(
      "-cp", classpathArgument,
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

  private def readResource(resource: String): String = {
    val sb = new StringBuilder
    val is = getClass.getResourceAsStream("/" + resource)
    require(is != null, s"resource [$resource] doesn't exist")
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
