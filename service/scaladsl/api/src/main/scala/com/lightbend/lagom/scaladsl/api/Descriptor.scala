/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import akka.NotUsed
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.deser.{ DefaultExceptionSerializer, ExceptionSerializer, MessageSerializer }
import com.lightbend.lagom.scaladsl.api.transport.{ HeaderFilter, Method, UserAgentHeaderFilter }

import scala.collection.immutable
import scala.reflect.ClassTag

/**
 * Describes a service.
 *
 * A descriptor is a set of call and topic descriptors that the service provides, coupled with metadata about how the
 * service and its calls are to be served. Metadata may include versioning and migrations, SLA's, sharding
 * hints, circuit breaker strategies etc.
 */
sealed trait Descriptor {
  import Descriptor._

  /**
   * The name of this service.
   *
   * This will be used for service lookups.
   */
  val name: String

  /**
   * The service calls that this service provides.
   */
  val calls: immutable.Seq[Call[_, _]]

  /**
   * The topics that this service publishes.
   */
  val topics: immutable.Seq[TopicCall[_]]

  /**
   * An exception serializer, used for handling exceptions on either end.
   */
  val exceptionSerializer: ExceptionSerializer

  /**
   * Whether this service should automatically have all its service calls made publicly routable by the service gateway.
   */
  val autoAcl: Boolean

  /**
   * The ACLs associated with this service.
   */
  val acls: immutable.Seq[ServiceAcl]

  /**
   * The header filter to apply across all service calls on this service
   */
  val headerFilter: HeaderFilter

  /**
   * Whether this service should be locatable by the service locator.
   */
  val locatableService: Boolean

  /**
   * The default circuit breaker to use for this service.
   */
  val circuitBreaker: CircuitBreaker

  def addCalls(calls: Call[_, _]*): Descriptor = withCalls(this.calls ++ calls: _*)
  def withCalls(calls: Call[_, _]*): Descriptor

  def addTopics(topics: TopicCall[_]*): Descriptor = withTopics(this.topics ++ topics: _*)
  def withTopics(topics: TopicCall[_]*): Descriptor

  def withExceptionSerializer(exceptionSerializer: ExceptionSerializer): Descriptor
  def withAutoAcl(autoAcl: Boolean): Descriptor

  def addAcls(acls: ServiceAcl*): Descriptor = withAcls(this.acls ++ acls: _*)
  def withAcls(acls: ServiceAcl*): Descriptor

  def withHeaderFilter(headerFilter: HeaderFilter): Descriptor
  def withLocatableService(locatableService: Boolean): Descriptor
  def withCircuitBreaker(circuitBreaker: CircuitBreaker): Descriptor
}

object Descriptor {

  def apply(name: String): Descriptor = {
    DescriptorImpl(name)
  }

  /**
   * Describes a service call.
   */
  sealed trait Call[Request, Response] {

    /**
     * The id of the call.
     *
     * Used for routing purposes, may be a name, or could be a REST method and path pattern.
     */
    val callId: CallId

    /**
     * A holder for the service call.
     *
     * This holds a reference to the service call, in an implementation specific way.
     */
    val serviceCallHolder: ServiceCallHolder

    /**
     * The request message serializer.
     */
    val requestSerializer: MessageSerializer[Request, _]

    /**
     * The response message serializer.
     */
    val responseSerializer: MessageSerializer[Response, _]

    /**
     * The configured circuit breaker.
     *
     * @return Some value if this service call wants to override the circuit breaker configured for its service,
     *         otherwise empty.
     */
    val circuitBreaker: Option[CircuitBreaker]

    /**
     * Whether this service call should automatically define an ACL for the router to route external calls to it.
     *
     * @return Some value if this service call explicitly decides that it should have an auto ACL defined for it,
     *         otherwise empty.
     */
    val autoAcl: Option[Boolean]

    /**
     * Return a copy of this call with the given service call holder configured.
     */
    def withServiceCallHolder(serviceCallHolder: ServiceCallHolder): Call[Request, Response]

    /**
     * Return a copy of this call with the given request serializer configured.
     */
    def withRequestSerializer(requestSerializer: MessageSerializer[Request, _]): Call[Request, Response]

    /**
     * Return a copy of this call with the given request serializer configured.
     */
    def withResponseSerializer(responseSerializer: MessageSerializer[Response, _]): Call[Request, Response]

    /**
     * Return a copy of this call with the given circuit breaker configured.
     *
     * This will override the circuit breaker configured on the service descriptor.
     */
    def withCircuitBreaker(circuitBreaker: CircuitBreaker): Call[Request, Response]

    /**
     * Return a copy of this call with autoAcl configured.
     *
     * This will override auto ACL setting configured on the service descriptor.
     */
    def withAutoAcl(autoAcl: Boolean): Call[Request, Response]
  }

  val NoCall: Call[NotUsed, NotUsed] = CallImpl(NamedCallId("NONE"), new ServiceCallHolder {},
    MessageSerializer.NotUsedMessageSerializer, MessageSerializer.NotUsedMessageSerializer, None, None)

  /**
   * A call identifier.
   *
   * Call identifiers can be a simple name, or can be more complex identifiers that use REST methods and paths to
   * extract information and correspondingly route calls.
   */
  sealed trait CallId

  /**
   * A named call identifier.
   */
  sealed trait NamedCallId extends CallId {

    /**
     * The name of the call.
     */
    val name: String
  }

  object NamedCallId {

    /**
     * Create a named call identifier with the given name.
     */
    def apply(name: String): NamedCallId = NamedCallIdImpl(name)
  }

  /**
   * A path based call identifier.
   */
  sealed trait PathCallId extends CallId {

    /**
     * The pattern for path.
     */
    val pathPattern: String
  }

  object PathCallId {

    /**
     * Create a path based call identifier with the given path pattern.
     */
    def apply(pathPattern: String): PathCallId = PathCallIdImpl(pathPattern)
  }

  /**
   * A REST/HTTP call identifier.
   */
  sealed trait RestCallId extends CallId {

    /**
     * The HTTP method for the call.
     *
     * The method will only be used for strict REST calls. For other calls, such as calls implemented by WebSockets
     * or other transports, the method may be ignored completely.
     */
    val method: Method

    /**
     * The pattern for the path.
     */
    val pathPattern: String
  }

  object RestCallId {

    /**
     * Create a REST call identifier with the given method and path pattern.
     */
    def apply(method: Method, pathPattern: String): RestCallId = RestCallIdImpl(method, pathPattern)
  }

  /**
   * Holds the service call itself.
   *
   * The implementations of this are intentionally opaque, as the mechanics of how the service call implementation
   * gets passed around is internal to Lagom.
   */
  trait ServiceCallHolder

  /**
   * Describes a message broker topic.
   */
  sealed trait TopicCall[Message] {

    /**
     * The identifier for teh topic.
     */
    val topicId: TopicId

    /**
     * Holds the topic implementation.
     */
    val topicHolder: TopicHolder

    /**
     * The serializer for the topics messages.
     */
    val messageSerializer: MessageSerializer[Message, ByteString]

    /**
     * Some implementation specific properties for the topic.
     */
    val properties: Properties[Message]

    /**
     * Return a copy of this topic call with the given topic holder configured.
     */
    def withTopicHolder(topicHolder: TopicHolder): TopicCall[Message]

    /**
     * Add a property to this topic call.
     */
    def addProperty[T](property: Property[Message, T], value: T): TopicCall[Message]
  }

  /**
   * Holds the topic implementation.
   *
   * The implementations of this are intentionally opaque, as the mechanics of how the service call implementation
   * gets passed around is internal to Lagom.
   */
  trait TopicHolder

  /**
   * Properties of a topic call.
   */
  sealed trait Properties[Message] {
    /**
     * Get the given property.
     */
    def get[T](property: Property[Message, T]): Option[T]

    /**
     * Add the given property.
     */
    def +[T](propertyValue: (Property[Message, T], T)): Properties[Message]
  }

  object Properties {
    def empty[Message]: Properties[Message] = PropertiesImpl(Map())
  }

  /**
   * A property.
   */
  sealed trait Property[-Message, T] {
    /**
     * The class of the value.
     */
    val valueClass: Class[T]

    /**
     * The name of the property.
     */
    val name: String
  }

  object Property {
    def apply[Message, T: ClassTag](name: String): Property[Message, T] = {
      PropertyImpl(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]], name)
    }
  }

  private[api] case class DescriptorImpl(
    name:                String,
    calls:               immutable.Seq[Call[_, _]]   = Nil,
    topics:              immutable.Seq[TopicCall[_]] = Nil,
    exceptionSerializer: ExceptionSerializer         = DefaultExceptionSerializer.Unresolved,
    autoAcl:             Boolean                     = false,
    acls:                immutable.Seq[ServiceAcl]   = Nil,
    headerFilter:        HeaderFilter                = UserAgentHeaderFilter,
    locatableService:    Boolean                     = true,
    circuitBreaker:      CircuitBreaker              = CircuitBreaker.PerNode
  ) extends Descriptor {
    override def withCalls(calls: Call[_, _]*): Descriptor = copy(calls = calls.to[immutable.Seq])
    override def withTopics(topics: TopicCall[_]*): Descriptor = copy(topics = topics.to[immutable.Seq])
    override def withExceptionSerializer(exceptionSerializer: ExceptionSerializer): Descriptor = copy(exceptionSerializer = exceptionSerializer)
    override def withAutoAcl(autoAcl: Boolean): Descriptor = copy(autoAcl = autoAcl)
    override def withAcls(acls: ServiceAcl*): Descriptor = copy(acls = acls.to[immutable.Seq])
    override def withHeaderFilter(headerFilter: HeaderFilter): Descriptor = copy(headerFilter = headerFilter)
    override def withLocatableService(locatableService: Boolean): Descriptor = copy(locatableService = locatableService)
    override def withCircuitBreaker(circuitBreaker: CircuitBreaker): Descriptor = copy(circuitBreaker = circuitBreaker)
  }

  private[api] case class CallImpl[Request, Response](
    callId:             CallId,
    serviceCallHolder:  ServiceCallHolder,
    requestSerializer:  MessageSerializer[Request, _],
    responseSerializer: MessageSerializer[Response, _],
    circuitBreaker:     Option[CircuitBreaker]         = None,
    autoAcl:            Option[Boolean]                = None
  ) extends Call[Request, Response] {
    override def withServiceCallHolder(serviceCallHolder: ServiceCallHolder): Call[Request, Response] = copy(serviceCallHolder = serviceCallHolder)
    override def withRequestSerializer(requestSerializer: MessageSerializer[Request, _]): Call[Request, Response] = copy(requestSerializer = requestSerializer)
    override def withResponseSerializer(responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] = copy(responseSerializer = responseSerializer)
    override def withCircuitBreaker(circuitBreaker: CircuitBreaker): Call[Request, Response] = copy(circuitBreaker = Some(circuitBreaker))
    override def withAutoAcl(autoAcl: Boolean): Call[Request, Response] = copy(autoAcl = Some(autoAcl))
  }

  private[api] case class NamedCallIdImpl(name: String) extends NamedCallId
  private[api] case class PathCallIdImpl(pathPattern: String) extends PathCallId
  private[api] case class RestCallIdImpl(method: Method, pathPattern: String) extends RestCallId

  private[api] case class TopicCallImpl[Message](
    topicId:           TopicId,
    topicHolder:       TopicHolder,
    messageSerializer: MessageSerializer[Message, ByteString],
    properties:        Properties[Message]                    = Properties.empty[Message]
  ) extends TopicCall[Message] {
    override def withTopicHolder(topicHolder: TopicHolder): TopicCall[Message] = copy(topicHolder = topicHolder)
    override def addProperty[T](property: Property[Message, T], value: T): TopicCall[Message] =
      copy(properties = properties + (property -> value))
  }

  private case class PropertyImpl[Message, T](valueClass: Class[T], name: String) extends Property[Message, T]
  private case class PropertiesImpl[Message](properties: Map[Property[Message, _], _]) extends Properties[Message] {
    override def get[T](property: Property[Message, T]): Option[T] = properties.get(property).asInstanceOf[Option[T]]
    override def +[T](propertyValue: (Property[Message, T], T)): Properties[Message] = PropertiesImpl(properties + propertyValue)
  }
}

sealed trait ServiceAcl {
  val method: Option[Method]
  val pathRegex: Option[String]
}

object ServiceAcl {
  def apply(method: Option[Method] = None, pathRegex: Option[String] = None): ServiceAcl = ServiceAclImpl(method, pathRegex)

  private case class ServiceAclImpl(method: Option[Method], pathRegex: Option[String]) extends ServiceAcl
}

sealed trait CircuitBreaker

object CircuitBreaker {
  case object None extends CircuitBreaker
  case object PerNode extends CircuitBreaker
  case object PerService extends CircuitBreaker

  sealed trait CircuitBreakerId extends CircuitBreaker {
    val id: String
  }

  def identifiedBy(id: String): CircuitBreaker = CircuitBreakerIdImpl(id)

  private case class CircuitBreakerIdImpl(id: String) extends CircuitBreakerId
}
