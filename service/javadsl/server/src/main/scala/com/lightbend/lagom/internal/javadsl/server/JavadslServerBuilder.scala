/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.server

import java.util.function.{ BiFunction, Function => JFunction }
import java.util.stream.Collectors
import javax.inject.{ Inject, Provider, Singleton }

import akka.stream.Materializer
import akka.util.ByteString
import com.lightbend.lagom.internal.api._
import com.lightbend.lagom.internal.javadsl.api._
import com.lightbend.lagom.internal.javadsl.client.JavadslServiceApiBridge
import com.lightbend.lagom.internal.server.ServiceRouter
import com.lightbend.lagom.javadsl.api.Descriptor.RestCallId
import com.lightbend.lagom.javadsl.api.deser.StreamedMessageSerializer
import com.lightbend.lagom.javadsl.api.transport.{ RequestHeader => _, _ }
import com.lightbend.lagom.javadsl.api.{ Descriptor, Service, ServiceInfo }
import com.lightbend.lagom.javadsl.jackson.{ JacksonExceptionSerializer, JacksonSerializerFactory }
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport.{ ClassServiceBinding, InstanceServiceBinding }
import com.lightbend.lagom.javadsl.server.{ PlayServiceCall, ServiceGuiceSupport }
import org.pcollections.{ HashTreePMap, TreePVector }
import play.api.http.HttpConfiguration
import play.api.inject.Injector
import play.api.mvc.{ RequestHeader => PlayRequestHeader, ResponseHeader => _, _ }
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.{ Environment, Logger }

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Turns a service implementation and descriptor into a Play router
 */
class JavadslServerBuilder @Inject() (environment: Environment, httpConfiguration: HttpConfiguration,
                                      jacksonSerializerFactory:   JacksonSerializerFactory,
                                      jacksonExceptionSerializer: JacksonExceptionSerializer)(implicit ec: ExecutionContext, mat: Materializer) {

  /**
   * Create a router for the given services.
   *
   * @param services An array of service interfaces to implementations.
   * @return The services.
   */
  def resolveServices(services: Seq[(Class[_], Any)]): ResolvedServices = {
    val resolvedDescriptors = services.map {
      case (interface, serviceImpl) if classOf[Service].isAssignableFrom(interface) =>
        val descriptor = ServiceReader.readServiceDescriptor(
          environment.classLoader,
          interface.asSubclass(classOf[Service])
        )
        ResolvedService(interface.asInstanceOf[Class[Any]], serviceImpl, resolveDescriptor(descriptor))
      case (interface, _) =>
        throw new IllegalArgumentException(s"Don't know how to load services that don't implement Service: $interface")
    }
    ResolvedServices(resolvedDescriptors)
  }

  /**
   * Resolve the given descriptor to the implementation of the service.
   */
  def resolveDescriptor(descriptor: Descriptor): Descriptor = {
    ServiceReader.resolveServiceDescriptor(descriptor, environment.classLoader,
      Map(JacksonPlaceholderSerializerFactory -> jacksonSerializerFactory),
      Map(JacksonPlaceholderExceptionSerializer -> jacksonExceptionSerializer))
  }

  /**
   * Create a service info for the given interfaces.
   */
  def createServiceInfo(primaryServiceInterface: Class[_], secondaryServices: Seq[Class[_]]): ServiceInfo = {
    val interfaces = primaryServiceInterface +: secondaryServices
    if (interfaces.forall(classOf[Service].isAssignableFrom)) {
      val descriptors = interfaces.map { serviceInterface =>
        ServiceReader.readServiceDescriptor(
          environment.classLoader,
          serviceInterface.asSubclass(classOf[Service])
        )
      }
      val locatableServices = descriptors
        .filter(_.locatableService())
        .map { descriptor =>
          descriptor.name() -> descriptor.acls()
        }.toMap.asJava
      new ServiceInfo(descriptors.head.name, HashTreePMap.from(locatableServices))
    } else {
      throw new IllegalArgumentException(s"Don't know how to load services that don't implement Service. Provided: ${interfaces.mkString("[", ", ", "]")}")
    }
  }
}

case class ResolvedServices(services: Seq[ResolvedService[_]])

case class ResolvedService[T](interface: Class[T], service: T, descriptor: Descriptor)

@Singleton
class ResolvedServicesProvider(bindings: Seq[ServiceGuiceSupport.ServiceBinding[_]]) extends Provider[ResolvedServices] {
  def this(bindings: Array[ServiceGuiceSupport.ServiceBinding[_]]) = this(bindings.toSeq)

  @Inject var serverBuilder: JavadslServerBuilder = null
  @Inject var injector: Injector = null

  lazy val get = {
    serverBuilder.resolveServices(bindings.map {
      case instance: InstanceServiceBinding[_] => (instance.serviceInterface, instance.service)
      case clazz: ClassServiceBinding[_]       => (clazz.serviceInterface, injector.instanceOf(clazz.serviceImplementation))
    })
  }
}

@Singleton
class JavadslServicesRouter @Inject() (resolvedServices: ResolvedServices, httpConfiguration: HttpConfiguration)(implicit
  ec: ExecutionContext,
                                                                                                                 mat: Materializer) extends SimpleRouter {

  private val serviceRouters = resolvedServices.services.map { service =>
    new JavadslServiceRouter(service.descriptor, service.service, httpConfiguration)
  }

  override val routes: Routes =
    serviceRouters.foldLeft(PartialFunction.empty[PlayRequestHeader, Handler])((routes, router) => routes.orElse(router.routes))

  override def documentation: Seq[(String, String, String)] = serviceRouters.flatMap(_.documentation)
}

class JavadslServiceRouter(override protected val descriptor: Descriptor, service: Any, httpConfiguration: HttpConfiguration)(implicit ec: ExecutionContext, mat: Materializer)
  extends ServiceRouter(httpConfiguration) with JavadslServiceApiBridge {

  private class JavadslServiceRoute(override val call: Call[Any, Any]) extends ServiceRoute {
    override val path: Path = JavadslPath.fromCallId(call.callId)
    override val method: Method = call.callId match {
      case rest: RestCallId => rest.method
      case _ => if (call.requestSerializer.isUsed) {
        Method.POST
      } else {
        Method.GET
      }
    }
    override val isWebSocket: Boolean = call.requestSerializer.isInstanceOf[StreamedMessageSerializer[_]] ||
      call.responseSerializer.isInstanceOf[StreamedMessageSerializer[_]]

    private val holder: MethodServiceCallHolder = call.serviceCallHolder() match {
      case holder: MethodServiceCallHolder => holder
    }

    override def createServiceCall(params: Seq[Seq[String]]): ServiceCall[Any, Any] = {
      holder.create(service, params).asInstanceOf[ServiceCall[Any, Any]]
    }
  }

  override protected val serviceRoutes: Seq[ServiceRoute] =
    descriptor.calls.asScala.map(call => new JavadslServiceRoute(call.asInstanceOf[Call[Any, Any]]))

  /**
   * Create the action.
   */
  override protected def action[Request, Response](call: Call[Request, Response], descriptor: Descriptor,
                                                   requestSerializer:  MessageSerializer[Request, ByteString],
                                                   responseSerializer: MessageSerializer[Response, ByteString], requestHeader: RequestHeader,
                                                   serviceCall: ServiceCall[Request, Response]): EssentialAction = {

    serviceCall match {
      // If it's a Play service call, then rather than creating the action directly, we let it create the action, and
      // pass it a callback that allows it to convert a service call into an action.
      case playServiceCall: PlayServiceCall[Request, Response] =>
        playServiceCall.invoke(
          new java.util.function.Function[ServiceCall[Request, Response], play.mvc.EssentialAction] {
            override def apply(serviceCall: ServiceCall[Request, Response]): play.mvc.EssentialAction = {
              createAction(serviceCall, call, descriptor, requestSerializer, responseSerializer, requestHeader).asJava
            }
          }
        )
      case _ =>
        createAction(serviceCall, call, descriptor, requestSerializer, responseSerializer, requestHeader)
    }
  }

  override protected def maybeLogException(exc: Throwable, log: => Logger, call: Call[_, _]) = {
    exc match {
      case _: NotFound | _: Forbidden => // no logging
      case e @ (_: UnsupportedMediaType | _: PayloadTooLarge | _: NotAcceptable) =>
        log.warn(e.getMessage)
      case e =>
        log.error(s"Exception in ${call.callId()}", e)
    }
  }

  override protected def invokeServiceCall[Request, Response](
    serviceCall:   ServiceCall[Request, Response],
    requestHeader: RequestHeader, request: Request
  ): Future[(ResponseHeader, Response)] = {
    serviceCall match {
      case play: PlayServiceCall[_, _] =>
        throw new IllegalStateException("Can't invoke a Play service call for WebSockets or as a service call passed in by another Play service call: " + play)
      case _ =>
        serviceCall.handleRequestHeader(new JFunction[RequestHeader, RequestHeader] {
          override def apply(t: RequestHeader) = requestHeader
        }).handleResponseHeader(new BiFunction[ResponseHeader, Response, (ResponseHeader, Response)] {
          override def apply(header: ResponseHeader, response: Response) = header -> response
        }).invoke(request).toScala
    }
  }

}
