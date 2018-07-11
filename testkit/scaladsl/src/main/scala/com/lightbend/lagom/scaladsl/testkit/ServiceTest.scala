/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig._
import com.lightbend.lagom.internal.testkit.CassandraTestServer
import com.lightbend.lagom.scaladsl.server.{ LagomApplication, LagomApplicationContext, RequiresLagomServicePort }
import play.api.inject.DefaultApplicationLifecycle
import play.api.{ ApplicationLoader, Environment, Play }
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
     * @param enabled True if Cassandra should be enabled, or false if disabled. Enabling Cassandra will also enable the
     * cluster.
     * @return A copy of this setup.
     */
    def withCassandra(enabled: Boolean): Setup

    /**
     * Enable Cassandra.
     *
     * If enabled, this will start an embedded Cassandra server before the tests start, and shut it down afterwards.
     * It will also configure Lagom to use the embedded Cassandra server. Enabling Cassandra will also enable the
     * cluster.
     *
     * @return A copy of this setup.
     */
    def withCassandra(): Setup = withCassandra(true)

    /**
     * Enable or disable JDBC.
     *
     * Enabling JDBC will also enable the cluster.
     *
     * @param enabled True if JDBC should be enabled, or false if disabled.
     * @return A copy of this setup.
     */
    def withJdbc(enabled: Boolean): Setup

    /**
     * Enable JDBC.
     *
     * Enabling JDBC will also enable the cluster.
     *
     * @return A copy of this setup.
     */
    def withJdbc(): Setup = withJdbc(true)

    /**
     * Enable or disable clustering.
     *
     * Disabling this will automatically disable any persistence plugins, since persistence requires clustering.
     *
     * @param enabled True if clustering should be enabled, or false if disabled.
     * @return A copy of this setup.
     */
    def withCluster(enabled: Boolean): Setup

    /**
     * Enable clustering.
     *
     * Disabling this will automatically disable any persistence plugins, since persistence requires clustering.
     *
     * @return A copy of this setup.
     */
    def withCluster(): Setup = withCluster(true)

    /**
     * Whether Cassandra is enabled.
     */
    def cassandra: Boolean

    /**
     * Whether JDBC is enabled.
     */
    def jdbc: Boolean

    /**
     * Whether clustering is enabled.
     */
    def cluster: Boolean

  }

  private case class SetupImpl(
    cassandra: Boolean = false,
    jdbc:      Boolean = false,
    cluster:   Boolean = false
  ) extends Setup {

    override def withCassandra(enabled: Boolean): Setup = {
      if (enabled) {
        copy(cassandra = true, cluster = true)
      } else {
        copy(cassandra = false)
      }
    }

    override def withJdbc(enabled: Boolean): Setup =
      if (enabled) {
        copy(jdbc = true, cluster = true)
      } else {
        copy(jdbc = false)
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
          // whether the future `asyncResult` was successful or failed, stop the server.
          asyncResult.andThen {
            case theResult => {
              testServer.stop()
              theResult
            }
          }.asInstanceOf[R]
        case syncResult =>
          testServer.stop()
          syncResult
      }
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

    val lifecycle = new DefaultApplicationLifecycle

    val config: Map[String, AnyRef] =
      if (setup.cassandra) {
        val now = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS").format(LocalDateTime.now())
        val testName = s"ServiceTest_$now"

        val cassandraPort = CassandraTestServer.run(testName, lifecycle)

        ClusterConfigMap ++ cassandraConfigMap(testName, cassandraPort)
      } else if (setup.jdbc) {
        ClusterConfigMap ++ JdbcConfigMap
      } else if (setup.cluster) {
        ClusterConfigMap
      } else {
        Map.empty
      }

    val lagomApplication =
      applicationConstructor(
        LagomApplicationContext(
          ApplicationLoader.createContext(
            environment = Environment.simple(),
            initialSettings = config,
            lifecycle = lifecycle
          )
        )
      )

    Play.start(lagomApplication.application)
    val serverConfig = ServerConfig(port = Some(0), mode = lagomApplication.environment.mode)
    val server = ServerProvider.defaultServerProvider.createServer(serverConfig, lagomApplication.application)

    lagomApplication match {
      case requiresPort: RequiresLagomServicePort =>
        requiresPort.provideLagomServicePort(server.httpPort.orElse(server.httpsPort).get)
      case other => ()
    }

    if (setup.cassandra || setup.jdbc) {
      awaitPersistenceInit(lagomApplication.actorSystem)
    }

    new TestServer[T](lagomApplication, server)
  }

}
