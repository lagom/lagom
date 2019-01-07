/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.function.{ Function => JFunction }

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.japi.function.{ Effect, Procedure }
import akka.stream.Materializer
import com.lightbend.lagom.devmode.ssl.LagomDevModeSSLHolder
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.javadsl.cluster.JoinClusterModule
import com.lightbend.lagom.internal.persistence.testkit.AwaitPersistenceInit.awaitPersistenceInit
import com.lightbend.lagom.internal.persistence.testkit.PersistenceTestConfig._
import com.lightbend.lagom.internal.testkit.TestkitSslSetup.Disabled
import com.lightbend.lagom.internal.testkit._
import com.lightbend.lagom.javadsl.api.{ Service, ServiceLocator }
import com.lightbend.lagom.javadsl.persistence.PersistenceModule
import com.lightbend.lagom.javadsl.pubsub.PubSubModule
import com.lightbend.lagom.spi.persistence.{ InMemoryOffsetStore, OffsetStore }
import javax.net.ssl.SSLContext
import play.Application
import play.api.inject.{ ApplicationLifecycle, BindingKey, DefaultApplicationLifecycle, bind => sBind }
import play.api.{ Configuration, Play }
import play.core.server.{ Server, ServerConfig, ServerProvider }
import play.inject.Injector
import play.inject.guice.GuiceApplicationBuilder

import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/**
 * Support for writing functional tests for one service. The service is running
 * in a server and in the test you can interact with it using its service client,
 * i.e. calls to the service API.
 *
 * Dependencies to other services must be replaced by stub or mock implementations by
 * overriding the bindings of the `GuiceApplicationBuilder` in the `Setup`.
 *
 * The server is ran standalone without persistence, pubsub or cluster features
 * enabled. Cassandra is also disabled by default. If your service require either of these features you
 * can enable them in the `Setup`.
 *
 * There are two different styles that can be used. It is most convenient to use [[#withServer withServer]],
 * since it automatically starts and stops the server before and after the given lambda.
 * When your test have several test methods, and especially when using persistence, it is
 * faster to only [[#startServer start]] the server once in a static method annotated with `@BeforeClass`
 * and stop it in a method annotated with `@AfterClass`.
 */
object ServiceTest {

  // These are all specified as strings so that we can say they are disabled without having a dependency on them.
  private val JdbcPersistenceModule = "com.lightbend.lagom.javadsl.persistence.jdbc.JdbcPersistenceModule"
  private val CassandraPersistenceModule = "com.lightbend.lagom.javadsl.persistence.cassandra.CassandraPersistenceModule"
  private val KafkaBrokerModule = "com.lightbend.lagom.internal.javadsl.broker.kafka.KafkaBrokerModule"
  private val KafkaClientModule = "com.lightbend.lagom.javadsl.broker.kafka.KafkaClientModule"

  sealed trait Setup {
    @deprecated(message = "Use withCassandra instead", since = "1.2.0")
    def withPersistence(enabled: Boolean): Setup = withCassandra(enabled)

    /**
     * Enable or disable Cassandra.
     *
     * If enabled, this will start an embedded Cassandra server before the tests start, and shut it down afterwards.
     * It will also configure Lagom to use the embedded Cassandra server. Enabling Cassandra will also enable the
     * cluster.
     *
     * @param enabled True if Cassandra should be enabled, or false if disabled.
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

    @deprecated(message = "Use configureBuilder instead", since = "1.2.0")
    def withConfigureBuilder(configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]) =
      this.configureBuilder(configureBuilder)

    /**
     * Configure the builder.
     *
     * Allows a function to be supplied to configure the Play Guice application builder. This allows components to be
     * mocked, modules to be enabled/disabled, and custom configuration to be supplied.
     *
     * @param configureBuilder The builder configuration function.
     * @return A copy of this setup.
     */
    def configureBuilder(configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]): Setup

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
     * Enable the SSL port.
     *
     * @return A copy of this setup.
     */
    @ApiMayChange
    def withSsl(): Setup = withSsl(true)

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

    /**
     * Whether HTTPS is enabled.
     */
    def ssl: Boolean

    /**
     * The builder configuration function
     */
    def configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]

  }

  private case class SetupImpl(
    cassandra:        Boolean,
    jdbc:             Boolean,
    cluster:          Boolean,
    ssl:              Boolean,
    configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]
  ) extends Setup {

    def this() = this(
      cassandra = false,
      jdbc = false,
      cluster = false,
      ssl = false,
      configureBuilder = new JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder] {
        override def apply(b: GuiceApplicationBuilder): GuiceApplicationBuilder = b
      }
    )

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

    override def withSsl(enabled: Boolean): Setup = {
      copy(ssl = enabled)
    }

    override def configureBuilder(configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]): Setup = {
      copy(configureBuilder = configureBuilder)
    }

  }

  /**
   * The default `Setup` configuration, which has persistence enabled.
   */
  val defaultSetup: Setup = new SetupImpl()

  /**
   * When the server is started you can get the service client and other
   * Guice bindings here.
   */
  class TestServer(
    val port:                           Int,
    val app:                            Application,
    server:                             Server,
    @ApiMayChange val clientSslContext: Optional[SSLContext] = Optional.empty()
  ) {

    @ApiMayChange val portSsl: Optional[Integer] = Optional.ofNullable(server.httpsPort.map(Integer.valueOf).orNull)

    /**
     * Get the service client for a service.
     */
    def client[S <: Service](serviceClass: Class[S]): S =
      app.injector().instanceOf(serviceClass)

    /**
     * Stream materializer. Useful for Akka Streams TestKit.
     */
    def materializer: Materializer = injector.instanceOf(classOf[Materializer])

    /**
     * Current Akka `ActorSystem`. Useful for Akka Streams TestKit.
     */
    def system: ActorSystem = injector.instanceOf(classOf[ActorSystem])

    /**
     * The Guice injector that can be used for retrieving anything
     * that has been bound to Guice.
     */
    def injector: Injector = app.injector()

    /**
     * If you use `startServer` you must also stop the server with
     * this method when the test is finished. That is handled automatically
     * by `withServer`.
     */
    def stop(): Unit = {
      Try(Play.stop(app.asScala()))
      Try(server.stop())
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
  def withServer(
    setup: Setup,
    block: Procedure[TestServer]
  ): Unit = {
    // using Procedure instead of Consumer to support throwing Exception
    val testServer = startServer(setup)
    try {
      block(testServer)
    } finally {
      testServer.stop()
    }
  }

  /**
   * Start the test server with the given `setup`. You must stop the server with
   * the `stop` method of the returned `TestServer` when the test is finished.
   *
   * When your test have several test methods, and especially when using persistence, it is
   * faster to only start the server once in a static method annotated with `@BeforeClass`
   * and stop it in a method annotated with `@AfterClass`. Otherwise [[#withServer]] is
   * more convenient.
   *
   * You can get the service client from the returned `TestServer`.
   */
  def startServer(setup: Setup): TestServer = {
    val port = Promise[Int]()
    val testServiceLocatorPort = TestServiceLocatorPort(port.future)

    val now = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS").format(LocalDateTime.now())
    val testName = s"ServiceTest_$now"

    val lifecycle = new DefaultApplicationLifecycle

    val initialBuilder = new GuiceApplicationBuilder()
      .bindings(sBind[TestServiceLocatorPort].to(testServiceLocatorPort))
      .bindings(sBind[ServiceLocator].to(classOf[TestServiceLocator]))
      .bindings(sBind[TopicFactory].to(classOf[TestTopicFactory]))
      .overrides(sBind[ApplicationLifecycle].to(lifecycle))
      .configure("play.akka.actor-system", testName)

    val finalBuilder =
      if (setup.cassandra) {
        val cassandraPort = CassandraTestServer.run(testName, lifecycle)

        initialBuilder
          .configure(cassandraConfig(testName, cassandraPort))
          .configure(ClusterConfig)
          .disableModules(JdbcPersistenceModule, KafkaClientModule, KafkaBrokerModule)

      } else if (setup.jdbc) {

        initialBuilder
          .configure(JdbcConfig)
          .configure(ClusterConfig)
          .disableModules(CassandraPersistenceModule, KafkaClientModule, KafkaBrokerModule)

      } else if (setup.cluster) {

        initialBuilder
          .configure(ClusterConfig)
          .disable(classOf[PersistenceModule])
          .bindings(play.api.inject.bind[OffsetStore].to[InMemoryOffsetStore])
          .disableModules(CassandraPersistenceModule, JdbcPersistenceModule, KafkaClientModule, KafkaBrokerModule)

      } else {

        initialBuilder
          .configure("akka.actor.provider", "akka.actor.LocalActorRefProvider")
          .disable(classOf[PersistenceModule], classOf[PubSubModule], classOf[JoinClusterModule])
          .bindings(play.api.inject.bind[OffsetStore].to[InMemoryOffsetStore])
          .disableModules(CassandraPersistenceModule, JdbcPersistenceModule, KafkaClientModule, KafkaBrokerModule)
      }

    val application = setup.configureBuilder(finalBuilder).build()

    Play.start(application.asScala())

    val sslSetup: TestkitSslSetup.TestkitSslSetup = if (setup.ssl) {
      val sslHolder = new LagomDevModeSSLHolder(application.environment().asScala())
      val clientSslContext: SSLContext = sslHolder.sslContext
      // In tests we're using a self-signed certificate so we use the same keyStore for both
      // the server and the client trustStore.
      TestkitSslSetup.enabled(sslHolder.keyStoreMetadata, sslHolder.trustStoreMetadata, clientSslContext)
    } else {
      Disabled
    }

    val props = System.getProperties
    val sslConfig: Configuration = Configuration.load(this.getClass.getClassLoader, props, sslSetup.sslSettings, allowMissingApplicationConf = true)
    val serverConfig: ServerConfig = new ServerConfig(
      port = Some(0),
      sslPort = sslSetup.sslPort,
      mode = application.environment().mode.asScala(),
      configuration = sslConfig,
      rootDir = application.environment().rootPath,
      address = "0.0.0.0",
      properties = props
    )
    val srv = ServerProvider.defaultServerProvider.createServer(serverConfig, application.asScala())
    val assignedPort = srv.httpPort.orElse(srv.httpsPort).get
    port.success(assignedPort)

    if (setup.cassandra || setup.jdbc) {
      val system = application.injector().instanceOf(classOf[ActorSystem])
      awaitPersistenceInit(system)
    }

    val javaSslContext = Optional.ofNullable(sslSetup.clientSslContext.orNull)
    new TestServer(assignedPort, application, srv, javaSslContext)
  }

  /**
   * Enriches [[GuiceApplicationBuilder]] with a `disableModules` method.
   */
  private implicit class GuiceBuilderOps(val builder: GuiceApplicationBuilder) extends AnyVal {
    def disableModules(classes: String*): GuiceApplicationBuilder = {
      val loadedClasses = classes.flatMap { className =>
        try {
          Seq(getClass.getClassLoader.loadClass(className))
        } catch {
          case cfne: ClassNotFoundException =>
            Seq.empty[Class[_]]
        }
      }
      if (loadedClasses.nonEmpty) {
        builder.disable(loadedClasses: _*)
      } else {
        builder
      }
    }
  }

  /**
   * Retry the give `block` (lambda) until it does not throw an exception or the timeout
   * expires, whichever comes first. If the timeout expires the last exception
   * is thrown. The `block` is retried with 100 milliseconds interval.
   */
  def eventually(max: FiniteDuration, block: Effect): Unit =
    eventually(max, 100.millis, block)

  /**
   * Retry the give `block` (lambda) until it does not throw an exception or the timeout
   * expires, whichever comes first. If the timeout expires the last exception
   * is thrown. The `block` is retried with the given `interval`.
   */
  def eventually(max: FiniteDuration, interval: FiniteDuration, block: Effect): Unit = {
    def now = System.nanoTime.nanos
    val stop = now + max

    @tailrec
    def poll(t: Duration): Unit = {
      val failed =
        try { block(); false } catch {
          case NonFatal(e) ⇒
            if ((now + t) >= stop) throw e
            true
        }
      if (failed) {
        Thread.sleep(t.toMillis)
        poll((stop - now) min interval)
      }
    }

    poll(max min interval)
  }

  /**
   * Create a binding that can be used with the `GuiceApplicationBuilder`
   * in the `Setup`, e.g. to override bindings to stub out dependencies to
   * other services.
   */
  def bind[T](clazz: Class[T]): BindingKey[T] =
    play.inject.Bindings.bind(clazz)

}
