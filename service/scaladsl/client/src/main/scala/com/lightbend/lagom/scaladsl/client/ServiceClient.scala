/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.lightbend.lagom.internal.client.CircuitBreakerMetricsProviderImpl
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactoryProvider
import com.lightbend.lagom.internal.scaladsl.client.{ ScaladslClientMacroImpl, ScaladslServiceClient, ScaladslServiceResolver, ScaladslWebSocketClient }
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.deser.{ DefaultExceptionSerializer, ExceptionSerializer }
import play.api.inject.{ ApplicationLifecycle, DefaultApplicationLifecycle }
import play.api.libs.concurrent.ActorSystemProvider
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Environment, Mode }

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.language.experimental.macros

/**
 * The Lagom service client implementor.
 *
 * Instances of this must also implement [[ServiceClientConstructor]], so that the `implementClient` macro can
 * generate code that constructs the service client.
 */
trait ServiceClient { self: ServiceClientConstructor =>

  /**
   * Implement a client for the given service descriptor.
   */
  def implement[S <: Service]: S = macro ScaladslClientMacroImpl.implementClient[S]
}

/**
 * Lagom service client constructor.
 *
 * This API should not be used directly, it will be invoked by the client generated by [[ServiceClient.implement]] in
 * order to construct the client and obtain the dependencies necessary for the client to operate.
 *
 * The reason for a separation between this interface and [[ServiceClient]] is so that the [[#construct]] method
 * doesn't appear on the user facing [[ServiceClient]] API. The macro it generates will cast the [[ServiceClient]] to
 * a [[ServiceClientConstructor]] in order to invoke it.
 *
 * Although this API should not directly be used by end users, the code generated by the [[ServiceClient]] macro does
 * cause end users to have a binary dependency on this class, which is why it's in the `scaladsl` package.
 */
trait ServiceClientConstructor extends ServiceClient {

  /**
   * Construct a service client, by invoking the passed in function that takes the implementation context.
   */
  def construct[S <: Service](constructor: ServiceClientImplementationContext => S): S
}

/**
 * The service client implementation context.
 *
 * This API should not be used directly, it will be invoked by the client generated by [[ServiceClient.implement]] in
 * order to resolve the service descriptor.
 *
 * The purpose of this API is to capture the dependencies required in order to implement a service client, such as the
 * HTTP and WebSocket clients.
 *
 * Although this API should not directly be used by end users, the code generated by the [[ServiceClient]] macro does
 * cause end users to have a binary dependency on this class, which is why it's in the `scaladsl` package.
 */
trait ServiceClientImplementationContext {

  /**
   * Resolve the given descriptor to a service client context.
   */
  def resolve(descriptor: Descriptor): ServiceClientContext
}

/**
 * The service client context.
 *
 * This API should not be used directly, it will be invoked by the client generated by [[ServiceClient.implement]] in
 * order to implement each service call and topic.
 *
 * The service client context is essentially a map of service calls and topics, constructed from a service descriptor,
 * that allows a [[ServiceCall]] to be easily constructed by the services methods.
 *
 * Although this API should not directly be used by end users, the code generated by the [[ServiceClient]] macro does
 * cause end users to have a binary dependency on this class, which is why it's in the `scaladsl` package.
 */
trait ServiceClientContext {
  /**
   * Create a service call for the given method name and passed in parameters.
   */
  def createServiceCall[Request, Response](methodName: String, params: immutable.Seq[Any]): ServiceCall[Request, Response]

  /**
   * Create a topic for the given method name.
   */
  def createTopic[Message](methodName: String): Topic[Message]
}

trait ServiceResolver {
  def resolve(descriptor: Descriptor): Descriptor
}

/**
 * The Lagom service client components.
 */
trait LagomServiceClientComponents extends TopicFactoryProvider with LagomConfigComponent {
  def wsClient: WSClient
  def serviceInfo: ServiceInfo
  def serviceLocator: ServiceLocator
  def materializer: Materializer
  def actorSystem: ActorSystem
  def executionContext: ExecutionContext
  def environment: Environment
  def applicationLifecycle: ApplicationLifecycle

  lazy val circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider = new CircuitBreakerMetricsProviderImpl(actorSystem)

  lazy val serviceResolver: ServiceResolver = new ScaladslServiceResolver(defaultExceptionSerializer)
  lazy val defaultExceptionSerializer: ExceptionSerializer = new DefaultExceptionSerializer(environment)
  lazy val scaladslWebSocketClient: ScaladslWebSocketClient = new ScaladslWebSocketClient(
    environment,
    config,
    applicationLifecycle
  )(executionContext)
  lazy val serviceClient: ServiceClient = new ScaladslServiceClient(wsClient, scaladslWebSocketClient, serviceInfo,
    serviceLocator, serviceResolver, optionalTopicFactory)(executionContext, materializer)
}

/**
 * Convenience for constructing service clients in a non Lagom server application.
 *
 * It is important to invoke [[#stop]] when the application is no longer needed, as this will trigger the shutdown
 * of all thread and connection pools.
 */
abstract class LagomClientApplication(
  clientName:  String,
  classLoader: ClassLoader = classOf[LagomClientApplication].getClassLoader
) extends LagomServiceClientComponents {
  private val defaultApplicationLifecycle = new DefaultApplicationLifecycle

  override lazy val serviceInfo: ServiceInfo = ServiceInfo(clientName, Map.empty)
  override lazy val environment: Environment = Environment(new File("."), classLoader, Mode.Prod)
  lazy val configuration: Configuration = Configuration.load(environment, Map.empty)
  override lazy val applicationLifecycle: ApplicationLifecycle = defaultApplicationLifecycle
  lazy val actorSystem: ActorSystem = new ActorSystemProvider(environment, configuration, applicationLifecycle).get

  override lazy val materializer: Materializer = ActorMaterializer.create(actorSystem)
  override lazy val executionContext: ExecutionContext = actorSystem.dispatcher

  /**
   * Stop the application.
   */
  def stop(): Unit = defaultApplicationLifecycle.stop()
}
