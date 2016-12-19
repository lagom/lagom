/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.lightbend.lagom.internal.client.{CircuitBreakerConfig, CircuitBreakerMetricsProviderImpl, CircuitBreakers}
import com.lightbend.lagom.internal.scaladsl.client.{ScaladslServiceClient, ScaladslServiceResolver, ScaladslWebSocketClient}
import com.lightbend.lagom.internal.scaladsl.registry.{ServiceRegistration, ServiceRegistry, ServiceRegistryServiceLocator}
import com.lightbend.lagom.internal.scaladsl.server.ScaladslServerMacroImpl
import com.lightbend.lagom.internal.spi.{CircuitBreakerMetricsProvider, ServiceAcl, ServiceDescription, ServiceDiscovery}
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceInfo, ServiceLocator}
import com.lightbend.lagom.scaladsl.client.{CircuitBreakingServiceLocator, LagomServiceClientComponents}
import play.api._
import play.api.ApplicationLoader.Context
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import play.core.DefaultWebCommands

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
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
  with LagomServerComponents
  with LagomServiceClientComponents {

  override implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
  override lazy val configuration: Configuration = Configuration.load(environment) ++
    context.playContext.initialConfiguration ++ additionalConfiguration

  /**
   * This can be overridden to provide any additional programatically configured configuration.
   */
  def additionalConfiguration: Configuration = Configuration.empty
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
 * Provides the Lagom dev mode components.
 *
 * This trait primarily has two responsibilities, it provides a service locator that uses Lagom's development service
 * locator, and it registers any services returned by the `serviceInfo` component with the Lagom develompent service
 * registry.
 *
 * It can be used both by Lagom services, and also by non Lagom services, such as pure Play applications, in order to
 * use the Lagom dev mode service locator and register components with Lagom. In the case of pure Play applications,
 * `serviceInfo` will need to manually be implemented to return the service name and any ACLs for the service gateway
 * to use.
 *
 * It expects the service locator URL to be provided using the `lagom.service-locator.url` property, which by default
 * will be automatically provided to the service by Lagom's dev mode build plugins.
 */
trait LagomDevModeComponents {
  /**
   * If being used in a Lagom service, this will be implemented by [[LagomServerComponents]], however if it's being
   * used by a Play application, this will need to be provided manually.
   */
  def serviceInfo: ServiceInfo
  def wsClient: WSClient
  def scaladslWebSocketClient: ScaladslWebSocketClient
  def environment: Environment
  def configuration: Configuration
  def executionContext: ExecutionContext
  def materializer: Materializer
  def actorSystem: ActorSystem
  def applicationLifecycle: ApplicationLifecycle

  lazy val circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider = new CircuitBreakerMetricsProviderImpl(actorSystem)
  lazy val circuitBreakerConfig: CircuitBreakerConfig = new CircuitBreakerConfig(configuration)
  lazy val circuitBreakers: CircuitBreakers = new CircuitBreakers(actorSystem, circuitBreakerConfig, circuitBreakerMetricsProvider)

  lazy val serviceRegistry: ServiceRegistry = {

    // We need to create our own static service locator since the service locator will depend on this service registry.
    val staticServiceLocator = new ServiceLocator {
      val serviceLocatorUrl = URI.create(configuration.underlying.getString("lagom.service-locator.url"))
      override def doWithService[T](name: String, serviceCall: Call[_, _])(block: (URI) => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
        if (name == ServiceRegistry.ServiceName) {
          block(serviceLocatorUrl).map(Some.apply)
        } else {
          Future.successful(None)
        }
      }
      override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
        if (name == ServiceRegistry.ServiceName) {
          Future.successful(Some(serviceLocatorUrl))
        } else {
          Future.successful(None)
        }
      }
    }

    val serviceClient = new ScaladslServiceClient(wsClient, scaladslWebSocketClient, serviceInfo, staticServiceLocator,
      new ScaladslServiceResolver(new DefaultExceptionSerializer(environment)), None)(executionContext, materializer)

    serviceClient.implement[ServiceRegistry]
  }

  lazy val serviceLocator: ServiceLocator = new ServiceRegistryServiceLocator(circuitBreakers, serviceRegistry, executionContext)

  // Eagerly register services
  new ServiceRegistration(serviceInfo, applicationLifecycle, configuration, serviceRegistry)(executionContext)
}
