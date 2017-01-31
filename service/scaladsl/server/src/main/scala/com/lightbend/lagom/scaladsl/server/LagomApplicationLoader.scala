/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import java.net.URI
import java.util.concurrent.{ TimeUnit, TimeoutException }

import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.actor.setup.ActorSystemSetup
import com.lightbend.lagom.internal.client.{ CircuitBreakerConfig, CircuitBreakerMetricsProviderImpl, CircuitBreakers }
import com.lightbend.lagom.internal.scaladsl.client.ScaladslServiceResolver
import com.lightbend.lagom.internal.scaladsl.server.ScaladslServerMacroImpl
import com.lightbend.lagom.internal.spi.{ ServiceAcl, ServiceDescription, ServiceDiscovery }
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.client.{ CircuitBreakingServiceLocator, LagomServiceClientComponents }
import com.lightbend.lagom.scaladsl.playjson.{ EmptyJsonSerializerRegistry, JsonSerializerRegistry, ProvidesJsonSerializerRegistry }
import com.typesafe.config.Config
import play.api._
import play.api.ApplicationLoader.Context
import play.core.DefaultWebCommands

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.language.experimental.macros

/**
 * A Play application loader for Lagom.
 *
 * Scala Lagom applications should provide a subclass of this to create their application, and configure it in their
 * `application.conf` file using:
 *
 * ```
 * play.application.loader = com.example.MyApplicationLoader
 * ```
 *
 * This class provides an abstraction over Play's application loader that provides Lagom specific functionality.
 */
abstract class LagomApplicationLoader extends ApplicationLoader with ServiceDiscovery {

  /**
   * Implementation of Play's load method.
   *
   * Since Lagom applications need to use a different wiring in dev mode, using the dev mode service locator and
   * registry, to what they use in prod, this separates the two possibilities out, invoking the
   * [[load()]] method for prod, and the [[loadDevMode()]] method for development.
   *
   * It also wraps the Play specific types in Lagom types.
   */
  override final def load(context: Context): Application = context.environment.mode match {
    case Mode.Dev => loadDevMode(LagomApplicationContext(context)).application
    case _        => load(LagomApplicationContext(context)).application
  }

  /**
   * Load a Lagom application for production.
   *
   * This should mix in a production service locator implementation, and anything else specific to production, to
   * an application provided subclass of [[LagomApplication]]. It will be invoked to load the application in
   * production.
   *
   * @param context The Lagom application context.
   * @return A Lagom application.
   */
  def load(context: LagomApplicationContext): LagomApplication

  /**
   * Load a Lagom application for development.
   *
   * This should mix in [[LagomDevModeComponents]] with an application provided subclass of [[LagomApplication]], such
   * that the service locator used will be the dev mode service locator, and so that services get registered with the
   * dev mode service registry.
   *
   * @param context The Lagom application context.
   * @return A lagom application.
   */
  def loadDevMode(context: LagomApplicationContext): LagomApplication = load(context)

  /**
   * Describe a service, for use when implementing [[describeServices]].
   */
  protected def readDescriptor[S <: Service]: Descriptor = macro ScaladslServerMacroImpl.readDescriptor[S]

  /**
   * Implement this to allow tooling, such as ConductR, to discover the services offered by this application.
   *
   * This will be used to generate configuration regarding ACLs and service names for production deployment.
   *
   * For example:
   *
   * ```
   * override def describeServices = List(
   *   readDescriptor[MyService]
   * )
   * ```
   */
  def describeServices: immutable.Seq[Descriptor] = Nil

  override final def discoverServices(classLoader: ClassLoader) = {
    import scala.collection.JavaConverters._
    import scala.compat.java8.OptionConverters._

    val serviceResolver = new ScaladslServiceResolver(DefaultExceptionSerializer.Unresolved)
    describeServices.map { descriptor =>
      val resolved = serviceResolver.resolve(descriptor)
      val convertedAcls = resolved.acls.map { acl =>
        new ServiceAcl {
          override def method() = acl.method.map(_.name).asJava
          override def pathPattern() = acl.pathRegex.asJava
        }.asInstanceOf[ServiceAcl]
      }.asJava
      new ServiceDescription {
        override def acls() = convertedAcls
        override def name() = resolved.name
      }.asInstanceOf[ServiceDescription]
    }.asJava
  }
}

/**
 * The Lagom application context.
 *
 * This wraps a Play context, but is provided such that if in future Lagom needs to pass other context information
 * to an app, this can be extended without breaking compatibility.
 */
sealed trait LagomApplicationContext {
  /**
   * The Play application loader context.
   */
  val playContext: Context
}

object LagomApplicationContext {
  /**
   * Create a Lagom application loader context.
   *
   * @param context The Play context to wrap.
   * @return A Lagom applciation context.
   */
  def apply(context: Context): LagomApplicationContext = new LagomApplicationContext {
    override val playContext: Context = context
  }

  /**
   * A test application loader context, useful when loading the application in unit or integration tests.
   */
  val Test = apply(Context(Environment.simple(), None, new DefaultWebCommands, Configuration.empty))
}

/**
 * A Lagom application.
 *
 * A Lagom service should subclass this in order to wire together a Lagom application.
 *
 * This includes the Lagom server components (which builds and provides the Lagom router) as well as the Lagom
 * service client components (which allows implementing Lagom service clients from Lagom service descriptors).
 *
 * There are two abstract defs that must be implemented, one is [[LagomServerComponents.lagomServer]], the other
 * is [[LagomServiceClientComponents.serviceLocator]]. Typically, the `lagomServer` component will be implemented by
 * an abstract subclass of this class, and will bind all the services that this Lagom application provides. Meanwhile,
 * the `serviceLocator` component will be provided by mixing in a service locator components trait in
 * [[LagomApplicationLoader]], which trait is mixed in will vary depending on whether the application is being loaded
 * for production or for development.
 *
 * @param context The application context.
 */
abstract class LagomApplication(context: LagomApplicationContext)
  extends BuiltInComponentsFromContext(context.playContext)
  with ProvidesAdditionalConfiguration
  with ProvidesJsonSerializerRegistry
  with LagomServerComponents
  with LagomServiceClientComponents {

  override implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
  override lazy val configuration: Configuration = Configuration.load(environment) ++
    additionalConfiguration.configuration ++ context.playContext.initialConfiguration

  override lazy val actorSystem: ActorSystem = {
    val (system, stopHook) = ActorSystemProvider.start(configuration, environment, optionalJsonSerializerRegistry)
    applicationLifecycle.addStopHook(stopHook)
    system
  }

  LagomServerTopicFactoryVerifier.verify(lagomServer, topicPublisherName)
}

private[server] object ActorSystemProvider {

  val logger = Logger(classOf[LagomApplication])

  /**
   * This is copied from Play's ActorSystemProvider, modified so we can inject json serializers
   */
  def start(configuration: Configuration, environment: Environment,
            serializerRegistry: Option[JsonSerializerRegistry]): (ActorSystem, () => Future[Unit]) = {
    val config = configuration.underlying
    val akkaConfig: Config = {
      val akkaConfigRoot = config.getString("play.akka.config")
      // Need to fallback to root config so we can lookup dispatchers defined outside the main namespace
      config.getConfig(akkaConfigRoot).withFallback(config)
    }

    val name = config.getString("play.akka.actor-system")

    val actorSystemSetup = ActorSystemSetup(
      BootstrapSetup(Some(environment.classLoader), Some(akkaConfig), None),
      JsonSerializerRegistry.serializationSetupFor(serializerRegistry.getOrElse(EmptyJsonSerializerRegistry))
    )
    val system = ActorSystem(name, actorSystemSetup)
    logger.debug(s"Starting application default Akka system: $name")

    val stopHook = { () =>
      logger.debug(s"Shutdown application default Akka system: $name")
      system.terminate()

      if (!config.getIsNull("play.akka.shutdown-timeout")) {
        val timeout = config.getDuration("play.akka.shutdown-timeout", TimeUnit.MILLISECONDS)
        try {
          // We do a blocking timeout. It'd be nice to be able to do an asynchronous timeout,
          // but what would we use to do that? The actor system is being shut down, we can't
          // use its scheduler.
          Await.result(system.whenTerminated, timeout.milliseconds)
        } catch {
          case te: TimeoutException =>
            // oh well.  We tried to be nice.
            logger.info(s"Could not shutdown the Akka system in $timeout milliseconds.  Giving up.")
        }
        Future.successful(())
      } else {
        // We just want to replace Terminated with Unit, we need an execution context to do that,
        // and can't use Akka's since by the time it's needed it's shut down. So we use a calling
        // thread execution context.
        system.whenTerminated.map(_ => ())(new ExecutionContext {
          override def reportFailure(cause: Throwable): Unit =
            cause.printStackTrace()
          override def execute(runnable: Runnable): Unit =
            runnable.run()
        })
      }
    }

    (system, stopHook)
  }
}

/**
 * When using dynamic port allocation, a circular dependency may exist if the application needs to know what port it's
 * running on, since the port won't be know until after the server has bound to the listening port (the OS will select
 * a free port at bind time), but we can't bind the server to the listening port until we have an application to
 * service incoming requests.
 *
 * This trait allows that circular dependency to be resolved, by making the port available as a future, and allowing it
 * to be provided after the application has been created using the
 * [[RequiresLagomServicePort.provideLagomServicePort()]] method.
 *
 * This is primarily useful for testing, where dynamic port allocation is often used.
 */
trait RequiresLagomServicePort {
  private val servicePortPromise = Promise[Int]()

  /**
   * The service port. This will be redeemed when [[provideLagomServicePort()]] is invoked.
   *
   * Anything that needs to know what port the app is running on can attach callbacks to this to handle it.
   */
  final val lagomServicePort: Future[Int] = servicePortPromise.future

  /**
   * Provide the Lagom service port.
   */
  final def provideLagomServicePort(port: Int): Unit = servicePortPromise.success(port)
}

/**
 * A service locator that just resolves locally provided services.
 *
 * This is useful for integration testing a single service, and can be mixed in to a [[LagomApplication]] class to
 * provide the local service locator.
 */
trait LocalServiceLocator extends RequiresLagomServicePort {
  def lagomServer: LagomServer
  def actorSystem: ActorSystem
  def executionContext: ExecutionContext
  def configuration: Configuration

  lazy val circuitBreakerConfig: CircuitBreakerConfig = new CircuitBreakerConfig(configuration)
  lazy val circuitBreakers = new CircuitBreakers(actorSystem, circuitBreakerConfig, new CircuitBreakerMetricsProviderImpl(actorSystem))
  lazy val serviceLocator: ServiceLocator = new CircuitBreakingServiceLocator(circuitBreakers)(executionContext) {
    val services = lagomServer.serviceBindings.map(_.descriptor.name).toSet

    def getUri(name: String): Future[Option[URI]] = lagomServicePort.map {
      case port if services(name) => Some(URI.create(s"http://localhost:$port"))
      case _                      => None
    }(executionContext)

    override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] =
      getUri(name)
  }
}

/**
 * Verifies that if there are any topics published by the Lagom server, that there is also a topic publisher.
 */
private[lagom] object LagomServerTopicFactoryVerifier {

  def verify(lagomServer: LagomServer, topicPublisherName: Option[String]): Unit = {
    topicPublisherName match {
      case None =>
        // No topic publisher has been provided, make sure there are no topics to publish
        lagomServer.serviceBindings.flatMap(_.descriptor.topics) match {
          case Nil =>
          // No problemo
          case some =>
            // Uh oh
            throw new NoTopicPublisherException("The bound Lagom server provides topics, but no topic publisher has been provided. " +
              "This can be resolved by mixing in a topic publisher trait, such as " +
              "com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents or " +
              "com.lightbend.lagom.scaladsl.testkit.TestTopicComponents into your application cake. " +
              "The topics published are " + some.mkString(", "))
        }
      case Some(_) =>
      // No need to do anything, a topic publisher has been provided
    }
  }

  class NoTopicPublisherException(msg: String) extends RuntimeException(msg)
}
