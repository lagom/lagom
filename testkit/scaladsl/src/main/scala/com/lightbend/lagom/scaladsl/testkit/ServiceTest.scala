/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.annotation.ApiMayChange
import com.lightbend.lagom.devmode.ssl.LagomDevModeSSLHolder
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig._
import com.lightbend.lagom.internal.testkit.TestkitSslSetup.Disabled
import com.lightbend.lagom.internal.testkit.CassandraTestServer
import com.lightbend.lagom.internal.testkit.TestkitSslSetup
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.RequiresLagomServicePort
import javax.net.ssl.SSLContext
import play.api.ApplicationLoader.Context
import play.api.inject.DefaultApplicationLifecycle
import play.api.Configuration
import play.api.Environment
import play.api.Play
import play.core.server.Server
import play.core.server.ServerConfig
import play.core.server.ServerProvider

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
     *                cluster.
     * @return A copy of this setup.
     */
    def withCassandra(enabled: Boolean): Setup

    /**
     * Enable or disable Cassandra on the specified port.
     *
     * @param enabled True if Cassandra should be enabled, or false if disabled. Enabling Cassandra will also enable the
     *                cluster.
     * @param port    The port to assign the Cassandra server to.
     * @return A copy of this setup.
     */
    def withCassandra(enabled: Boolean, port: Int): Setup

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
     * Enable or disable the SSL port.
     *
     * @param enabled True if the server should bind an HTTP+TLS port, or false if only HTTP should be bound.
     * @return A copy of this setup.
     */
    @ApiMayChange
    def withSsl(enabled: Boolean): Setup

    /**
     * Enable or disable SSL on the specified port.
     * @param enabled True if the server should bind an HTTP+TLS port, or false if only HTTP should be bound.
     * @param port The port to bind to.
     * @return A copy of this setup.
     */
    @ApiMayChange
    def withSsl(enabled: Boolean, port: Int): Setup

    /**
     * Enable the SSL port.
     *
     * @return A copy of this setup.
     */
    @ApiMayChange
    def withSsl(): Setup = withSsl(true)

    /**
     * Sets the HTTP port the server should run on.
     *
     * By default, a dynamically assigned port will be used.
     *
     * @param port The port to assign the server to.
     * @return A copy of this setup.
     */
    def withPort(port: Int): Setup

    /**
     * The server HTTP port.
     */
    def port: Int

    /**
     * Whether Cassandra is enabled.
     */
    def cassandra: Boolean

    /**
     * The Cassandra server port.
     */
    def cassandraPort: Int

    /**
     * Whether JDBC is enabled.
     */
    def jdbc: Boolean

    /**
     * Whether clustering is enabled.
     */
    def cluster: Boolean

    /**
     * Whether SSL is enabled.
     */
    def ssl: Boolean

    /**
     * The server HTTPS port.
     */
    def sslPort: Int
  }

  private case class SetupImpl(
      port: Int = 0,
      cassandra: Boolean = false,
      cassandraPort: Int = 0,
      jdbc: Boolean = false,
      cluster: Boolean = false,
      ssl: Boolean = false,
      sslPort: Int = 0
  ) extends Setup {
    override def withCassandra(enabled: Boolean): Setup = withCassandra(enabled, port = 0)
    override def withCassandra(enabled: Boolean, port: Int): Setup = {
      if (enabled) {
        copy(cassandra = true, cassandraPort = port, cluster = true)
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

    override def withSsl(enabled: Boolean): Setup = withSsl(enabled, port = 0)
    override def withSsl(enabled: Boolean, port: Int): Setup = {
      copy(ssl = enabled, sslPort = port)
    }

    override def withPort(port: Int): Setup = {
      copy(port = port)
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
  final class TestServer[A <: LagomApplication] private[testkit] (
      val application: A,
      val playServer: Server,
      @ApiMayChange val clientSslContext: Option[SSLContext] = None
  ) {

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
   * the `block` returns or throws the test server will automatically be stopped.
   *
   * This method should be used when the server can be started and stopped for each test
   * method. When your test have several test methods, and especially when using persistence, it is
   * faster to only start the server once with [[#startServer]].
   *
   * You can get the service client from the `TestServer` that is passed as parameter
   * to the `block`.
   */
  def withServer[T <: LagomApplication, R](
      setup: Setup
  )(applicationConstructor: LagomApplicationContext => T)(block: TestServer[T] => R): R = {
    val testServer = startServer(setup)(applicationConstructor)
    try {
      val result = block(testServer)

      // In order to work with the ScalaTest async matcher support, if the returned value is a future, we must not
      // shutdown the server until that future has finished executing.
      result match {
        case asyncResult: Future[_] =>
          import testServer.executionContext
          // whether the future `asyncResult` was successful or failed, stop the server.
          asyncResult
            .andThen {
              case theResult => {
                testServer.stop()
                theResult
              }
            }
            .asInstanceOf[R]
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
  def startServer[T <: LagomApplication](
      setup: Setup
  )(applicationConstructor: LagomApplicationContext => T): TestServer[T] = {
    val lifecycle = new DefaultApplicationLifecycle

    val config: Map[String, AnyRef] =
      if (setup.cassandra) {
        val now      = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS").format(LocalDateTime.now())
        val testName = s"ServiceTest_$now"

        val cassandraPort = CassandraTestServer.run(testName, lifecycle, setup.cassandraPort)

        cassandraConfigMap(testName, cassandraPort)
      } else if (setup.jdbc) {
        JdbcConfigMap
      } else if (setup.cluster) {
        ClusterConfigMap
      } else {
        BasicConfigMap
      }

    val environment = Environment.simple()
    val lagomApplication =
      applicationConstructor(
        LagomApplicationContext(
          Context.create(
            environment = environment,
            initialSettings = config,
            lifecycle = lifecycle
          )
        )
      )

    Play.start(lagomApplication.application)

    val sslSetup: TestkitSslSetup.TestkitSslSetup = if (setup.ssl) {
      val sslHolder                    = new LagomDevModeSSLHolder(environment)
      val clientSslContext: SSLContext = sslHolder.sslContext
      // In tests we're using a self-signed certificate so we use the same keyStore for both
      // the server and the client trustStore.
      TestkitSslSetup.enabled(
        sslHolder.keyStoreMetadata,
        sslHolder.trustStoreMetadata,
        clientSslContext,
        Some(setup.sslPort)
      )
    } else {
      Disabled
    }

    val props = System.getProperties
    val sslConfig: Configuration =
      Configuration.load(this.getClass.getClassLoader, props, sslSetup.sslSettings, allowMissingApplicationConf = true)

    val serverConfig: ServerConfig = new ServerConfig(
      port = Some(setup.port),
      sslPort = sslSetup.sslPort,
      mode = lagomApplication.environment.mode,
      configuration = sslConfig,
      rootDir = environment.rootPath,
      address = "0.0.0.0",
      properties = props
    )
    val server = ServerProvider.defaultServerProvider.createServer(serverConfig, lagomApplication.application)

    lagomApplication match {
      case requiresPort: RequiresLagomServicePort =>
        requiresPort.provideLagomServicePort(server.httpPort.orElse(server.httpsPort).get)
      case other => ()
    }

    if (setup.cassandra || setup.jdbc) {
      awaitPersistenceInit(lagomApplication.actorSystem)
    }

    new TestServer[T](lagomApplication, server, sslSetup.clientSslContext)
  }
}
