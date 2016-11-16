/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.function.{ Function => JFunction }

import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal
import com.lightbend.lagom.internal.javadsl.cluster.JoinClusterModule
import com.lightbend.lagom.internal.testkit.{ TestServiceLocator, TestServiceLocatorPort, TestTopicFactory }
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.persistence.PersistenceModule
import com.lightbend.lagom.javadsl.persistence.testkit.TestUtil
import com.lightbend.lagom.javadsl.pubsub.PubSubModule
import akka.actor.ActorSystem
import akka.japi.function.Effect
import akka.japi.function.Procedure
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.Materializer
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.internal.persistence.{ InMemoryOffsetStore, OffsetStore }
import org.apache.cassandra.io.util.FileUtils
import play.Application
import play.Configuration
import play.api.Logger
import play.api.Mode
import play.api.Play
import play.api.inject.BindingKey
import play.api.inject.{ bind => sBind }
import play.core.server.Server
import play.core.server.ServerConfig
import play.core.server.ServerProvider
import play.inject.Injector
import play.inject.guice.GuiceApplicationBuilder

/**
 * Support for writing functional tests for one service. The service is running
 * in a server and in the test you can interact with it using its service client,
 * i.e. calls to the service API.
 *
 * Dependencies to other services must be replaced by stub or mock implementations by
 * overriding the bindings of the `GuiceApplicationBuilder` in the `Setup`.
 *
 * The server is by default running with persistence, pubsub and cluster features
 * enabled. Cassandra is also started before the test server is started. If your service
 * does not use these features you can disable them in the `Setup`, which will reduce
 * the startup time.
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
  private val KafkaBrokerModule = "com.lightbend.lagom.internal.broker.kafka.KafkaBrokerModule"
  private val KafkaClientModule = "com.lightbend.lagom.javadsl.broker.kafka.KafkaClientModule"

  sealed trait Setup {
    @deprecated(message = "Use withCassandra instead", since = "1.2.0")
    def withPersistence(enabled: Boolean): Setup = withCassandra(enabled)

    /**
     * Enable or disable Cassandra.
     *
     * If enabled, this will start an embedded Cassandra server before the tests start, and shut it down afterwards.
     * It will also configure Lagom to use the embedded Cassandra server.
     *
     * @param enabled True if Cassandra should be enabled, or false if disabled.
     * @return A copy of this setup.
     */
    def withCassandra(enabled: Boolean): Setup

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

    /**
     * The builder configuration function
     */
    def configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]

  }

  private case class SetupImpl(cassandra: Boolean, cluster: Boolean,
                               configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]) extends Setup {

    def this() = this(
      cassandra = false,
      cluster = false,
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

    override def configureBuilder(configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]): Setup = {
      copy(configureBuilder = configureBuilder)
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
  val defaultSetup: Setup = new SetupImpl()

  /**
   * When the server is started you can get the service client and other
   * Guice bindings here.
   */
  class TestServer(val port: Int, val app: Application, server: Server) {

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
      Try(Play.stop(app.getWrappedApplication))
      Try(server.stop())
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
   * and stop it in a method annotated with `@AfterClass`. Otherwise [[#withServer withServer]] is
   * more convenient.
   *
   * You can get the service client from the returned `TestServer`.
   */
  def startServer(setup: Setup): TestServer = {
    val port = Promise[Int]()
    val testServiceLocatorPort = TestServiceLocatorPort(port.future)

    val now = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS").format(LocalDateTime.now())
    val testName = s"ServiceTest_$now"

    val b1 = new GuiceApplicationBuilder()
      .bindings(sBind[TestServiceLocatorPort].to(testServiceLocatorPort))
      .bindings(sBind[ServiceLocator].to(classOf[TestServiceLocator]))
      .bindings(sBind[TopicFactory].to(classOf[TestTopicFactory]))
      .configure("play.akka.actor-system", testName)

    val log = Logger(getClass)

    val b3 =
      if (setup.cassandra) {
        val cassandraPort = CassandraLauncher.randomPort
        val cassandraDirectory = Files.createTempDirectory(testName).toFile
        FileUtils.deleteRecursiveOnExit(cassandraDirectory)
        val t0 = System.nanoTime()
        CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 0)
        log.debug(s"Cassandra started in ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms")
        val b2 = b1.configure(new Configuration(TestUtil.persistenceConfig(testName, cassandraPort, useServiceLocator = false)))
          .configure("lagom.cluster.join-self", "on")
        disableModules(b2, KafkaClientModule, KafkaBrokerModule)
      } else if (setup.cluster) {
        val b2 = b1.configure(new Configuration(TestUtil.clusterConfig))
          .configure("lagom.cluster.join-self", "on")
          .disable(classOf[PersistenceModule])
          .bindings(play.api.inject.bind[OffsetStore].to[InMemoryOffsetStore])
        disableModules(b2, CassandraPersistenceModule, KafkaClientModule, KafkaBrokerModule)
      } else {
        val b2 = b1.configure("akka.actor.provider", "akka.actor.LocalActorRefProvider")
          .disable(classOf[PersistenceModule], classOf[PubSubModule], classOf[JoinClusterModule])
          .bindings(play.api.inject.bind[OffsetStore].to[InMemoryOffsetStore])
        disableModules(b2, CassandraPersistenceModule, JdbcPersistenceModule, KafkaClientModule, KafkaBrokerModule)
      }

    val application = setup.configureBuilder(b3).build()

    Play.start(application.getWrappedApplication)
    val serverConfig = ServerConfig(port = Some(0), mode = Mode.Test)
    val srv = ServerProvider.defaultServerProvider.createServer(serverConfig, application.getWrappedApplication)
    val assignedPort = srv.httpPort.orElse(srv.httpsPort).get
    port.success(assignedPort)

    if (setup.cassandra) {
      val system = application.injector().instanceOf(classOf[ActorSystem])
      TestUtil.awaitPersistenceInit(system)
    }

    new TestServer(assignedPort, application, srv)
  }

  private def disableModules(builder: GuiceApplicationBuilder, classes: String*): GuiceApplicationBuilder = {
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
