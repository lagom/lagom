/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import akka.persistence.cassandra.testkit.CassandraLauncher
import com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil
import com.lightbend.lagom.scaladsl.server.{ LagomApplication, LagomApplicationContext, RequiresLagomServicePort }
import org.apache.cassandra.io.util.FileUtils
import org.slf4j.LoggerFactory
import play.api.ApplicationLoader.Context
import play.api.{ Configuration, Environment, Play }
import play.core.DefaultWebCommands
import play.core.server.{ Server, ServerConfig, ServerProvider }

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

/**
 * Support for writing functional tests for one service. The service is running in a server and in the test you can
 * interact with it using its service client, i.e. calls to the service API.
 *
 * The server is ran standalone without persistence, pubsub or cluster features enabled. Cassandra is also disabled by
 * default. If your service require either of these features you can enable them in the `Setup`.
 *
 * There are two different styles that can be used. It is most convenient to use [[ServiceTest.withServer()]], since it
 * automatically starts and stops the server before and after the given block. When your test have several test
 * methods, and especially when using persistence, it is faster to only [[ServiceTest.startServer()]] the server once in a
 * before all tests hook, and then stop it in an after all test hook.
 */
object ServiceTest {

  sealed trait Setup {
    /**
     * Enable or disable Cassandra.
     *
     * @param enabled True if Cassandra should be enabled, or false if disabled.
     * @return A copy of this setup.
     */
    def withCassandra(enabled: Boolean): Setup

    /**
     * Enable clustering.
     *
     * Disabling this will automatically disable any persistence plugins, since persistence requires clustering.
     *
     * @param enabled True if clustering should be enabled, or false if disabled.
     * @return A copy of this setup.
     */
    def withCluster(enabled: Boolean): Setup

    /**
     * Whether Cassandra is enabled.
     */
    def cassandra: Boolean

    /**
     * Whether clustering is enabled.
     */
    def cluster: Boolean

  }

  private case class SetupImpl(cassandra: Boolean = false, cluster: Boolean = false) extends Setup {

    override def withCassandra(enabled: Boolean): Setup = {
      if (enabled) {
        copy(cassandra = true, cluster = true)
      } else {
        copy(cassandra = false)
      }
    }

    override def withCluster(enabled: Boolean): Setup = {
      if (enabled) {
        copy(cluster = true)
      } else {
        copy(cluster = false, cassandra = false)
      }
    }
  }

  /**
   * The default `Setup` configuration, which has persistence enabled.
   */
  val defaultSetup: Setup = SetupImpl()

  /**
   * When the server is started you can get the service client and other
   * Guice bindings here.
   */
  final class TestServer[A <: LagomApplication] private[testkit] (val application: A, val playServer: Server) {

    /**
     * Convenient access to the materializer
     */
    implicit def materializer = application.materializer

    /**
     * Convenient access to the execution context
     */
    implicit def executionContext = application.executionContext

    /**
     * Convenient access to the service client
     */
    def serviceClient = application.serviceClient

    /**
     * Convenient access to the actor system
     */
    def actorSystem = application.actorSystem

    /**
     * If you use `startServer` you must also stop the server with
     * this method when the test is finished. That is handled automatically
     * by `withServer`.
     */
    def stop(): Unit = {
      Try(Play.stop(application.application))
      Try(playServer.stop())
      Try(CassandraLauncher.stop())
    }
  }

  /**
   * Start the test server with the given `setup` and run the `block` (lambda). When
   * the `block returns or throws the test server will automatically be stopped.
   *
   * This method should be used when the server can be started and stopped for each test
   * method. When your test have several test methods, and especially when using persistence, it is
   * faster to only start the server once with [[#startServer]].
   *
   * You can get the service client from the `TestServer` that is passed as parameter
   * to the `block`.
   */
  def withServer[T <: LagomApplication, R](setup: Setup)(applicationConstructor: LagomApplicationContext => T)(block: TestServer[T] => R): R = {
    val testServer = startServer(setup)(applicationConstructor)
    try {
      val result = block(testServer)

      // In order to work with the ScalaTest async matcher support, if the returned value is a future, we must not
      // shutdown the server until that future has finished executing.
      result match {
        case asyncResult: Future[_] =>
          import testServer.executionContext
          asyncResult.onComplete { _ =>
            testServer.stop()
          }
        case syncResult =>
          testServer.stop()
      }

      result
    } catch {
      case NonFatal(e) =>
        testServer.stop()
        throw e
    }
  }

  /**
   * Start the test server with the given `setup`. You must stop the server with
   * the `stop` method of the returned `TestServer` when the test is finished.
   *
   * When your test have several test methods, and especially when using persistence, it is
   * faster to only start the server once in a static method annotated with `@BeforeClass`
   * and stop it in a method annotated with `@AfterClass`. Otherwise [[withServer()]] is
   * more convenient.
   *
   * You can get the service client from the returned `TestServer`.
   */
  def startServer[T <: LagomApplication](setup: Setup)(applicationConstructor: LagomApplicationContext => T): TestServer[T] = {

    val now = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS").format(LocalDateTime.now())
    val testName = s"ServiceTest_$now"

    val log = LoggerFactory.getLogger(getClass)

    val config =
      if (setup.cassandra) {
        val cassandraPort = CassandraLauncher.randomPort
        val cassandraDirectory = Files.createTempDirectory(testName).toFile
        FileUtils.deleteRecursiveOnExit(cassandraDirectory)
        val t0 = System.nanoTime()
        CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 0)
        log.debug(s"Cassandra started in ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms")
        Configuration(TestUtil.persistenceConfig(testName, cassandraPort, useServiceLocator = false)) ++
          Configuration("lagom.cluster.join-self" -> "on")
      } else if (setup.cluster) {
        Configuration("lagom.cluster.join-self" -> "on")
      } else {
        Configuration.empty
      }

    val lagomApplication = applicationConstructor(LagomApplicationContext(Context(Environment.simple(), None, new DefaultWebCommands, config)))

    Play.start(lagomApplication.application)
    val serverConfig = ServerConfig(port = Some(0), mode = lagomApplication.environment.mode)
    val server = ServerProvider.defaultServerProvider.createServer(serverConfig, lagomApplication.application)
    lagomApplication match {
      case requiresPort: RequiresLagomServicePort =>
        requiresPort.provideLagomServicePort(server.httpPort.orElse(server.httpsPort).get)
      case other => ()
    }
    if (setup.cassandra) {
      TestUtil.awaitPersistenceInit(lagomApplication.actorSystem)
    }

    new TestServer[T](lagomApplication, server)
  }
}
