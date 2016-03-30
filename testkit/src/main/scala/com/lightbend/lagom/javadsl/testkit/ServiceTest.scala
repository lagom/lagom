/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.function.{ Function => JFunction }

import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

import com.lightbend.lagom.internal.cluster.JoinClusterModule
import com.lightbend.lagom.internal.persistence.cassandra.CassandraConfigProvider
import com.lightbend.lagom.internal.testkit.TestServiceLocator
import com.lightbend.lagom.internal.testkit.TestServiceLocatorPort
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.persistence.PersistenceModule
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraConfig
import com.lightbend.lagom.javadsl.persistence.testkit.TestUtil
import com.lightbend.lagom.javadsl.pubsub.PubSubModule

import akka.actor.ActorSystem
import akka.japi.function.Effect
import akka.japi.function.Procedure
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.Materializer
import javax.inject.Singleton
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

  case class Setup(persistence: Boolean, cluster: Boolean,
                   configureBuilder: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]) {

    def this() = this(
      persistence = true,
      cluster = true,
      configureBuilder = new JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder] {
      override def apply(b: GuiceApplicationBuilder): GuiceApplicationBuilder = b
    }
    )

    /**
     * Disable or enable persistence, including Cassandra startup.
     */
    def withPersistence(enabled: Boolean): Setup =
      copy(persistence = enabled)

    /**
     * Disable or enable cluster and pubsub.
     * If cluster is disabled the persistence is also disabled,
     * since cluster is a prerequisite for persistence.
     */
    def withCluster(enabled: Boolean): Setup = {
      if (enabled) copy(cluster = true)
      else copy(cluster = false, persistence = false)
    }

    /**
     * Transformation of the Guice builder. Can for example be used to override bindings
     * to stub out dependencies to other services.
     */
    def withConfigureBuilder(transformation: JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder]): Setup =
      copy(configureBuilder = transformation)
  }

  /**
   * The default `Setup` configuration, which has persistence enabled.
   */
  val defaultSetup: Setup = new Setup()

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
      .bindings(sBind[CassandraConfig].toProvider(classOf[CassandraConfigProvider]))
      .bindings(sBind[ServiceLocator].to(classOf[TestServiceLocator]))
      .configure("play.akka.actor-system", testName)

    val log = Logger(getClass)

    val b2 =
      if (setup.persistence) {
        val cassandraPort = CassandraLauncher.randomPort
        val cassandraDirectory = new File("target/" + testName)
        val t0 = System.nanoTime()
        CassandraLauncher.start(cassandraDirectory, CassandraLauncher.DefaultTestConfigResource, clean = true, port = 0)
        log.debug(s"Cassandra started in ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms")
        b1.configure(new Configuration(TestUtil.persistenceConfig(testName, cassandraPort, useServiceLocator = true)))
          .configure("lagom.cluster.join-self", "on")
      } else if (setup.cluster)
        b1.configure(new Configuration(TestUtil.clusterConfig))
          .configure("lagom.cluster.join-self", "on")
          .disable(classOf[PersistenceModule])
      else
        b1.configure("akka.actor.provider", "akka.actor.LocalActorRefProvider")
          .disable(classOf[PersistenceModule], classOf[PubSubModule], classOf[JoinClusterModule])

    val application = setup.configureBuilder(b2).build()

    Play.start(application.getWrappedApplication)
    val serverConfig = ServerConfig(port = Some(0), mode = Mode.Test)
    val srv = ServerProvider.defaultServerProvider.createServer(serverConfig, application.getWrappedApplication)
    val assignedPort = srv.httpPort.orElse(srv.httpsPort).get
    port.success(assignedPort)

    if (setup.persistence) {
      val system = application.injector().instanceOf(classOf[ActorSystem])
      TestUtil.awaitPersistenceInit(system)
    }

    new TestServer(assignedPort, application, srv)
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
