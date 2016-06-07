/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.sbt.run

import java.io.Closeable
import java.net.{ URI, URL }
import java.util.{ Map => JMap }

import collection.JavaConverters._
import java.io.File

import sbt._
import play.sbt.Colors

import scala.concurrent.{ ExecutionContext, Future }

private[sbt] object Servers {

  def tryStop(log: Logger): Unit = {
    ServiceLocator.tryStop(log)
    CassandraServer.tryStop(log)
  }

  def asyncTryStop(log: Logger)(implicit ecn: ExecutionContext) = {
    val f = Future.traverse(Seq(ServiceLocator, CassandraServer))(ser => Future { ser.tryStop(log) })
    f.onComplete(_ => println("Servers stopped"))
    f
  }

  abstract class ServerContainer {
    protected type Server

    protected var server: Server = _

    final def tryStop(log: Logger): Unit = synchronized {
      if (server != null) stop(log)
    }

    protected def stop(log: Logger): Unit
  }

  object ServiceLocator extends ServerContainer {
    protected type Server = Closeable {
      def start(serviceLocatorPort: Int, serviceGatewayPort: Int, unmanagedServices: JMap[String, String]): Unit
      def serviceLocatorAddress: URI
      def serviceGatewayAddress: URI
    }

    def start(log: Logger, parentClassLoader: ClassLoader, classpath: Array[URL], serviceLocatorPort: Int, serviceGatewayPort: Int, unmanagedServices: Map[String, String]): Unit = synchronized {
      if (server == null) {
        withContextClassloader(new java.net.URLClassLoader(classpath, parentClassLoader)) { loader =>
          val serverClass = loader.loadClass("com.lightbend.lagom.discovery.ServiceLocatorServer")
          server = serverClass.newInstance().asInstanceOf[Server]
          try {
            server.start(serviceLocatorPort, serviceGatewayPort, unmanagedServices.asJava)
          } catch {
            case e: Exception =>
              val msg = "Failed to start embedded Service Locator or Service Gateway. " +
                s"Hint: Are ports ${serviceLocatorPort} and ${serviceGatewayPort} already in use?"
              stop()
              throw new RuntimeException(msg, e)
          }
        }
      }
      if (server != null) {
        log.info("Service locator is running at " + server.serviceLocatorAddress)
        log.info("Service gateway is running at " + server.serviceGatewayAddress)
      }
    }

    private def withContextClassloader[T](loader: ClassLoader)(body: ClassLoader => T): T = {
      val current = Thread.currentThread().getContextClassLoader()
      try {
        Thread.currentThread().setContextClassLoader(loader)
        body(loader)
      } finally Thread.currentThread().setContextClassLoader(current)
    }

    protected def stop(log: Logger): Unit = synchronized {
      if (server == null) {
        log.info("Service locator was already stopped")
      } else {
        log.info("Stopping service locator")
        stop()
      }
    }

    private def stop(): Unit = synchronized {
      try server.close()
      catch { case _: Exception => () }
      finally server = null
    }
  }

  private[sbt] object CassandraServer extends ServerContainer {
    import sbt._
    import java.lang.Runtime
    import scala.concurrent.duration._
    import com.datastax.driver.core.Cluster
    import scala.util.control.NonFatal
    import scala.util.Try
    import com.lightbend.lagom.sbt.LagomPlugin

    protected case class CassandraProcess(process: Process, port: Int) {
      private val killOnExitCallback = new Thread(new Runnable() {
        override def run(): Unit = kill()
      })

      def hostname: String = "127.0.0.1"
      def address: String = s"$hostname:$port"

      // Needed to make sure the spawned process is killed when the current process (i.e., the sbt console) is shut down
      private[CassandraServer] def enableKillOnExit(): Unit = Runtime.getRuntime.addShutdownHook(killOnExitCallback)
      private[CassandraServer] def disableKillOnExit(): Unit = Runtime.getRuntime.removeShutdownHook(killOnExitCallback)
      private[CassandraServer] def kill(): Unit = Try(process.destroy())
    }

    protected type Server = CassandraProcess

    def start(log: Logger, cp: Seq[File], port: Int, cleanOnStart: Boolean, jvmOptions: Seq[String], maxWaiting: FiniteDuration): Unit = synchronized {
      if (server != null) log.info(s"Cassandra is running at ${server.address}")
      else {
        // see https://github.com/krasserm/akka-persistence-cassandra/blob/5efcd16bfc5a72ad277cb5687a62542f00ae8857/src/main/scala/akka/persistence/cassandra/testkit/CassandraLauncher.scala#L29-L31
        val args = Array(port.toString, cleanOnStart.toString)
        val classpathOption = Path.makeString(cp)
        val options = Seq[String](
          "-classpath", classpathOption,
          "akka.persistence.cassandra.testkit.CassandraLauncher",
          port.toString,
          cleanOnStart.toString
        )
        val process = Fork.java.fork(ForkOptions(runJVMOptions = jvmOptions), options)
        server = CassandraProcess(process, port)
        server.enableKillOnExit()
        waitForRunningCassandra(log, server, maxWaiting)
      }
    }

    private def waitForRunningCassandra(log: Logger, server: CassandraProcess, maxWaiting: FiniteDuration): Unit = {
      val contactPoint = Seq(new java.net.InetSocketAddress(server.hostname, server.port.toInt)).asJava
      val clusterBuilder = Cluster.builder.addContactPointsWithPorts(contactPoint)

      @annotation.tailrec
      def tryConnect(deadline: Deadline): Unit = {
        print(".") // each attempts prints a dot (informing the user of progress) 
        try {
          import scala.collection.JavaConverters._
          val session = clusterBuilder.build().connect()
          println() // we don't want to print the message on the same line of the dots... 
          log.info("Cassandra server running at " + server.address)
          session.closeAsync()
          session.getCluster().closeAsync()
        } catch {
          case _: Exception =>
            if (deadline.hasTimeLeft()) {
              // wait a bit before trying again
              Thread.sleep(500)
              tryConnect(deadline)
            } else {
              val msg = s"""Cassandra server is not yet started.\n
                           |The value assigned to
                           |`${LagomPlugin.autoImport.lagomCassandraMaxBootWaitingTime.key.label}` 
                           |is either too short, or this may indicate another 
                           |process is already running on port ${server.port}""".stripMargin
              println() // we don't want to print the message on the same line of the dots...
              log.info(Colors.red(msg))
            }
        }
      }
      log.info("Starting embedded Cassandra server")
      tryConnect(maxWaiting fromNow)
    }

    protected def stop(log: Logger): Unit = synchronized {
      if (server == null) {
        log.info("Cassandra was already stopped")
      } else {
        log.info("Stopping cassandra")
        try server.kill()
        finally {
          try server.disableKillOnExit()
          catch {
            case NonFatal(_) => ()
          } finally server = null
        }
      }
    }
  }
}
