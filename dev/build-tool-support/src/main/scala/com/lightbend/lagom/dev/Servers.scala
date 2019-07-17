/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.dev

import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.{Optional, Map => JMap}

import play.dev.filewatch.LoggerProxy

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.language.reflectiveCalls

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

    /**
     * Each ServerContainer implementation needs to define the Server type using structural typing.
     * This is needed because the server classes are not available on the classloader used by the tooling.
     *
     * The alternative would be to use classical java reflection: find the method with args we plan to pass and invoke it.
     * Using structural typing make it much more convenient for us, but maybe not obvious at first sight.
     * Hence, this long explanation. ;-)
     */
    protected type Server

    protected class ServerProcess(process: Process) {
      private val killOnExitCallback = new Thread(new Runnable() {
        override def run(): Unit = kill()
      })

      // Needed to make sure the spawned process is killed when the current process (i.e., the sbt console) is shut down
      private[Servers] def enableKillOnExit(): Unit  = Runtime.getRuntime.addShutdownHook(killOnExitCallback)
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

      // use CompletionStage / Runnable / Thread in case scala equivalent are not available on classloader.
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

    final def tryStop(log: LoggerProxy): Unit =
      synchronized {
        if (server != null) stop(log)
      }

    protected def stop(log: LoggerProxy): Unit
  }

  object ServiceLocator extends ServerContainer {
    protected type Server = Closeable {
      def start(
          serviceLocatorAddress: String,
          serviceLocatorPort: Int,
          serviceGatewayAddress: String,
          serviceGatewayHttpPort: Int,
          unmanagedServices: JMap[String, String],
          gatewayImpl: String
      ): Unit
      def serviceLocatorAddress: URI
      def serviceGatewayAddress: URI
    }

    def start(
        log: LoggerProxy,
        parentClassLoader: ClassLoader,
        classpath: Array[URL],
        serviceLocatorAddress: String,
        serviceLocatorPort: Int,
        serviceGatewayAddress: String,
        serviceGatewayHttpPort: Int,
        unmanagedServices: Map[String, String],
        gatewayImpl: String
    ): Closeable =
      synchronized {
        if (server == null) {
          withContextClassloader(new java.net.URLClassLoader(classpath, parentClassLoader)) { loader =>
            val serverClass = loader.loadClass("com.lightbend.lagom.registry.impl.ServiceLocatorServer")
            server = serverClass.getDeclaredConstructor().newInstance().asInstanceOf[Server]
            try {
              server.start(
                serviceLocatorAddress,
                serviceLocatorPort,
                serviceGatewayAddress,
                serviceGatewayHttpPort,
                unmanagedServices.asJava,
                gatewayImpl
              )
            } catch {
              case e: Exception =>
                val msg = "Failed to start embedded Service Locator or Service Gateway. " +
                  s"Hint: Are ports $serviceLocatorPort or $serviceGatewayHttpPort already in use?"
                stop()
                throw new RuntimeException(msg, e)
            }
          }
        }
        if (server != null) {
          log.info("Service locator is running at " + server.serviceLocatorAddress)
          // TODO: trace all valid locations for the service gateway.
          log.info("Service gateway is running at " + server.serviceGatewayAddress)
        }

        new Closeable {
          override def close(): Unit = stop(log)
        }
      }

    private def withContextClassloader[T](loader: ClassLoader)(body: ClassLoader => T): T = {
      val current = Thread.currentThread().getContextClassLoader
      try {
        Thread.currentThread().setContextClassLoader(loader)
        body(loader)
      } finally Thread.currentThread().setContextClassLoader(current)
    }

    protected def stop(log: LoggerProxy): Unit =
      synchronized {
        if (server == null) {
          log.info("Service locator was already stopped")
        } else {
          log.info("Stopping service locator")
          stop()
        }
      }

    private def stop(): Unit =
      synchronized {
        try server.close()
        catch { case _: Exception => () } finally server = null
      }
  }

  private[lagom] object CassandraServer extends ServerContainer {
    protected type Server = {
      def start(
         cassandraDirectory: File,
         yamlConfig: File,
         clean: Boolean,
         port: Optional[Int],
         loader: ClassLoader,
         maxWaiting: java.time.Duration
      ): Unit
      def stop(): Unit
      def address: String
      def hostname: String
      def port: Int
    }

    def start(
        log: LoggerProxy,
        parentClassLoader: ClassLoader,
        classpath: Seq[File],
        port: Int,
        cleanOnStart: Boolean,
        yamlConfig: File,
        maxWaiting: FiniteDuration
    ): Closeable =
      synchronized {
        if (server != null) {
          log.info(s"Cassandra is running at ${server.address}")
        } else {
          val loader      = new java.net.URLClassLoader(classpath.map(_.toURI.toURL).toArray, parentClassLoader)
          val directory   = new File("target/embedded-cassandra")
          val serverClass = loader.loadClass("com.lightbend.lagom.internal.cassandra.CassandraLauncher")
          server = serverClass.getDeclaredConstructor().newInstance().asInstanceOf[Server]

          log.info("Starting Cassandra")
          try {
            server.start(
              directory, yamlConfig, cleanOnStart, Optional.of(port), loader, java.time.Duration.ofNanos(maxWaiting.toNanos)
            )
          } catch {
            case t: Throwable => {
              val msg = s"""Cassandra server is not yet started.\n
                           |The value assigned to
                           |`lagomCassandraMaxBootWaitingTime`
                           |is either too short, or this may indicate another
                           |process is already running on port ${server.port}""".stripMargin
              println() // we don't want to print the message on the same line of the dots...
              log.info(msg)
              throw t
            }
          }
          println() // we don't want to print the message on the same line of the dots...
          log.info(s"Cassandra is running at ${server.address}")
        }
        new Closeable {
          override def close(): Unit = stop(log)
        }
      }

    protected def stop(log: LoggerProxy): Unit =
      synchronized {
        if (server == null) {
          log.info("Cassandra was already stopped")
        } else {
          log.info("Stopping cassandra")
          stop()
        }
      }

    private def stop(): Unit =
      synchronized {
        try {
          server.stop()
        } catch {
          case _: Exception => ()
        } finally {
          server = null.asInstanceOf[Server]
        }
      }
  }

  private[lagom] object KafkaServer extends ServerContainer {
    protected class KafkaProcess(process: Process) extends ServerProcess(process)

    protected type Server = KafkaProcess

    def start(
        log: LoggerProxy,
        cp: Seq[File],
        kafkaPort: Int,
        zooKeeperPort: Int,
        kafkaPropertiesFile: Option[File],
        jvmOptions: Seq[String],
        targetDir: File,
        cleanOnStart: Boolean
    ): Closeable = {

      val args =
        kafkaPort.toString ::
          zooKeeperPort.toString ::
          targetDir.getAbsolutePath ::
          cleanOnStart.toString ::
          kafkaPropertiesFile.toList.map(_.getAbsolutePath)

      val log4jOutput   = targetDir.getAbsolutePath + java.io.File.separator + "log4j_output"
      val sysProperties = List(s"-Dkafka.logs.dir=$log4jOutput")
      val process = LagomProcess.runJava(
        jvmOptions.toList ::: sysProperties,
        cp,
        "com.lightbend.lagom.internal.kafka.KafkaLauncher",
        args
      )
      server = new KafkaProcess(process)
      server.completionHook.thenAccept(
        new Consumer[Int] {
          override def accept(exitCode: Int): Unit = if (exitCode != 0) println("Kafka Server closed unexpectedly.")
        }
      )
      server.enableKillOnExit()
      log.info("Starting Kafka")
      log.debug(s"Kafka log output can be found under $log4jOutput.")

      new Closeable {
        override def close(): Unit = stop(log)
      }
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
      "cas_native"   -> s"tcp://127.0.0.1:$lagomCassandraPort/cas_native",
      "kafka_native" -> s"tcp://$lagomKafkaAddress/kafka_native"
    )
  }
}
