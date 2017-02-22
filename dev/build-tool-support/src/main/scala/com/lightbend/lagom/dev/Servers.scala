/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

import java.io.{ Closeable, File }
import java.net.{ URI, URL }
import java.util.concurrent.{ CompletableFuture, CompletionStage, TimeUnit }
import java.util.function.Consumer
import java.util.{ Properties, Map => JMap }

import com.datastax.driver.core.Cluster
import play.dev.filewatch.LoggerProxy

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

private[lagom] object Servers {

  private val servers = Seq(ServiceLocator, CassandraServer, KafkaServer)

  def tryStop(log: LoggerProxy): Unit = {
    servers.foreach(_.tryStop(log))
  }

  def asyncTryStop(log: LoggerProxy)(implicit ecn: ExecutionContext): Future[Unit] = {
    val f = Future.traverse(servers)(server => Future { server.tryStop(log) })
    f.andThen { case _ => log.info("All servers stopped") }.map(_ => ())
  }

  abstract class ServerContainer {
    protected type Server

    protected class ServerProcess(process: Process) {
      private val killOnExitCallback = new Thread(new Runnable() {
        override def run(): Unit = kill()
      })

      // Needed to make sure the spawned process is killed when the current process (i.e., the sbt console) is shut down
      private[Servers] def enableKillOnExit(): Unit = Runtime.getRuntime.addShutdownHook(killOnExitCallback)
      private[Servers] def disableKillOnExit(): Unit = Runtime.getRuntime.removeShutdownHook(killOnExitCallback)
      private[Servers] def kill(): Unit = {
        // Note, don't use scala.util.Try, since it may have not been loaded yet, and the classloader that has it may
        // have already been shutdown when the shutdown hook is executed (this really does happen in maven).
        try {
          promise.complete(0) // TODO: replace int with proper typing
          process.destroy()
          if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
          }
        } catch {
          case NonFatal(_) =>
          // ignore, it's executed from a shutdown hook, not much we can or should do
        }
      }

      // use CompletionStage / Runable / Thread in case scala equivalent are not available on classloader.
      private val promise: CompletableFuture[Int] = new CompletableFuture[Int]()
      new Thread(new Runnable {
        override def run(): Unit = {
          process.waitFor()
          // if process completes via Kill, this promise.complete is ignored.
          promise.complete(process.exitValue()) // TODO: replace int with proper typing
        }
      }).start()
      def completionHook: CompletionStage[Int] = promise
    }

    protected var server: Server = _

    final def tryStop(log: LoggerProxy): Unit = synchronized {
      if (server != null) stop(log)
    }

    protected def stop(log: LoggerProxy): Unit
  }

  object ServiceLocator extends ServerContainer {
    protected type Server = Closeable {
      def start(serviceLocatorPort: Int, serviceGatewayPort: Int, unmanagedServices: JMap[String, String]): Unit
      def serviceLocatorAddress: URI
      def serviceGatewayAddress: URI
    }

    def start(log: LoggerProxy, parentClassLoader: ClassLoader, classpath: Array[URL], serviceLocatorPort: Int,
      serviceGatewayPort: Int, unmanagedServices: Map[String, String]): Unit = synchronized {
      if (server == null) {
        withContextClassloader(new java.net.URLClassLoader(classpath, parentClassLoader)) { loader =>
          val serverClass = loader.loadClass("com.lightbend.lagom.discovery.ServiceLocatorServer")
          server = serverClass.newInstance().asInstanceOf[Server]
          try {
            server.start(serviceLocatorPort, serviceGatewayPort, unmanagedServices.asJava)
          } catch {
            case e: Exception =>
              val msg = "Failed to start embedded Service Locator or Service Gateway. " +
                s"Hint: Are ports $serviceLocatorPort and $serviceGatewayPort already in use?"
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
      val current = Thread.currentThread().getContextClassLoader
      try {
        Thread.currentThread().setContextClassLoader(loader)
        body(loader)
      } finally Thread.currentThread().setContextClassLoader(current)
    }

    protected def stop(log: LoggerProxy): Unit = synchronized {
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

  private[lagom] object CassandraServer extends ServerContainer {
    import scala.concurrent.duration._
    import scala.util.Try
    import scala.util.control.NonFatal

    protected case class CassandraProcess(process: Process, port: Int) extends ServerProcess(process) {
      def hostname: String = "127.0.0.1"
      def address: String = s"$hostname:$port"
    }

    protected type Server = CassandraProcess

    def start(log: LoggerProxy, cp: Seq[File], port: Int, cleanOnStart: Boolean, jvmOptions: Seq[String], maxWaiting: FiniteDuration): Unit = synchronized {
      if (server != null) log.info(s"Cassandra is running at ${server.address}")
      else {
        // see https://github.com/krasserm/akka-persistence-cassandra/blob/5efcd16bfc5a72ad277cb5687a62542f00ae8857/src/main/scala/akka/persistence/cassandra/testkit/CassandraLauncher.scala#L29-L31
        val args = List(
          port.toString,
          cleanOnStart.toString
        )
        val process = LagomProcess.runJava(jvmOptions.toList, cp, "com.lightbend.lagom.internal.cassandra.CassandraLauncher", args)
        server = CassandraProcess(process, port)
        server.enableKillOnExit()
        waitForRunningCassandra(log, server, maxWaiting)
      }
    }

    private def waitForRunningCassandra(log: LoggerProxy, server: CassandraProcess, maxWaiting: FiniteDuration): Unit = {
      val contactPoint = Seq(new java.net.InetSocketAddress(server.hostname, server.port)).asJava
      val clusterBuilder = Cluster.builder.addContactPointsWithPorts(contactPoint)

      @annotation.tailrec
      def tryConnect(deadline: Deadline): Unit = {
        print(".") // each attempts prints a dot (informing the user of progress) 
        try {
          val session = clusterBuilder.build().connect()
          println() // we don't want to print the message on the same line of the dots... 
          log.info("Cassandra server running at " + server.address)
          session.closeAsync()
          session.getCluster.closeAsync()
        } catch {
          case _: Exception =>
            if (deadline.hasTimeLeft()) {
              // wait a bit before trying again
              Thread.sleep(500)
              tryConnect(deadline)
            } else {
              val msg = s"""Cassandra server is not yet started.\n
                           |The value assigned to
                           |`lagomCassandraMaxBootWaitingTime`
                           |is either too short, or this may indicate another 
                           |process is already running on port ${server.port}""".stripMargin
              println() // we don't want to print the message on the same line of the dots...
              log.info(msg)
            }
        }
      }
      log.info("Starting Cassandra")
      tryConnect(maxWaiting.fromNow)
    }

    protected def stop(log: LoggerProxy): Unit = synchronized {
      if (server == null) {
        log.info("Cassandra was already stopped")
      } else {
        log.info("Stopping Cassandra...")
        try {
          server.kill()
          log.info("Cassandra is stopped.")
        } finally {
          try server.disableKillOnExit()
          catch {
            case NonFatal(_) => ()
          } finally server = null
        }
      }
    }
  }

  private[lagom] object KafkaServer extends ServerContainer {
    protected class KafkaProcess(process: Process) extends ServerProcess(process)

    protected type Server = KafkaProcess

    def start(log: LoggerProxy, cp: Seq[File], kafkaPort: Int, zooKeperPort: Int, kafkaPropertiesFile: Option[File], jvmOptions: Seq[String], targetDir: File, cleanOnStart: Boolean): Unit = {
      val args = kafkaPort.toString :: zooKeperPort.toString :: targetDir.getAbsolutePath :: cleanOnStart.toString :: kafkaPropertiesFile.toList.map(_.getAbsolutePath)

      val log4jOutput = targetDir.getAbsolutePath + java.io.File.separator + "log4j_output"
      val sysProperties = List(s"-Dkafka.logs.dir=$log4jOutput")
      val process = LagomProcess.runJava(jvmOptions.toList ::: sysProperties, cp, "com.lightbend.lagom.internal.kafka.KafkaLauncher", args)
      server = new KafkaProcess(process)
      server.completionHook.thenAccept(
        new Consumer[Int] {
          override def accept(exitCode: Int): Unit = if (exitCode != 0) println("Kafka Server closed unexpectedly.")
        }
      )
      server.enableKillOnExit()
      log.info("Starting Kafka")
      log.debug(s"Kafka log output can be found under ${log4jOutput}.")
    }

    protected def stop(log: LoggerProxy): Unit = {
      if (server == null) {
        log.info("Kafka was already stopped")
      } else {
        log.info("Stopping Kafka...")
        try {
          server.kill()
          log.info("Kafka is stopped.")
        } finally {
          try server.disableKillOnExit()
          catch {
            case NonFatal(_) => ()
          } finally server = null
        }
      }
    }
  }
}

private[lagom] object StaticServiceLocations {
  def staticServiceLocations(lagomCassandraPort: Int, lagomKafkaAddress: String): Map[String, String] = {
    Map(
      "cas_native" -> s"tcp://127.0.0.1:$lagomCassandraPort/cas_native",
      "kafka_native" -> s"tcp://$lagomKafkaAddress/kafka_native"
    )
  }
}
