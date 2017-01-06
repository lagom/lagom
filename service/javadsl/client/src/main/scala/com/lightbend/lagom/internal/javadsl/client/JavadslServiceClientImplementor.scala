/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client

import java.lang.reflect.{ InvocationHandler, Method }
import java.util.function
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }
import org.slf4j.LoggerFactory
import com.google.inject.Inject
import com.lightbend.lagom.javadsl.api.{ Descriptor, ServiceCall, ServiceInfo, ServiceLocator }
import com.lightbend.lagom.javadsl.api.Descriptor.Call
import com.lightbend.lagom.javadsl.api.broker.Topic
import com.lightbend.lagom.javadsl.api.deser._
import com.lightbend.lagom.javadsl.api.transport._
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import javax.inject.Singleton

import com.lightbend.lagom.internal.client.ClientServiceCallInvoker
import com.lightbend.lagom.internal.javadsl.api.{ JavadslPath, MethodServiceCallHolder, MethodTopicHolder }
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactoryProvider
import play.api.Environment
import play.api.libs.ws.WSClient

/**
 * Implements a service client.
 */
@Singleton
class JavadslServiceClientImplementor @Inject() (ws: WSClient, webSocketClient: JavadslWebSocketClient, serviceInfo: ServiceInfo,
                                                 serviceLocator: ServiceLocator, environment: Environment,
                                                 topicFactoryProvider: TopicFactoryProvider)(implicit ec: ExecutionContext, mat: Materializer) {

  private val log = LoggerFactory.getLogger(classOf[JavadslServiceClientImplementor])

  def implement[T](interface: Class[T], descriptor: Descriptor): T = {
    java.lang.reflect.Proxy.newProxyInstance(environment.classLoader, Array(interface), new ServiceClientInvocationHandler(descriptor)).asInstanceOf[T]
  }

  class ServiceClientInvocationHandler(descriptor: Descriptor) extends InvocationHandler {
    private def serviceCallMethods: Map[Method, JavadslServiceCallInvocationHandler[Any, Any]] = descriptor.calls().asScala.map { call =>
      call.serviceCallHolder() match {
        case holder: MethodServiceCallHolder =>
          holder.method -> new JavadslServiceCallInvocationHandler[Any, Any](ws, webSocketClient, serviceInfo, serviceLocator,
            descriptor, call.asInstanceOf[Call[Any, Any]], holder)
      }
    }.toMap

    private def topicMethods: Map[Method, _] = {
      descriptor.topicCalls.asScala.map { topicCall =>
        topicCall.topicHolder match {
          case holder: MethodTopicHolder =>
            topicFactoryProvider.get match {
              case Some(topicFactory) =>
                holder.method -> topicFactory.create(topicCall)
              case None => holder.method -> NoTopicFactory
            }
        }
      }.toMap
    }

    private val methods: Map[Method, _] = serviceCallMethods ++ topicMethods

    override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]): AnyRef = {
      methods.get(method) match {
        case Some(serviceCallInvocationHandler: JavadslServiceCallInvocationHandler[_, _]) => serviceCallInvocationHandler.invoke(args)
        case Some(topic: Topic[_]) => topic
        case Some(NoTopicFactory) => throw new IllegalStateException("Attempt to get a topic, but there is no TopicFactory provided to implement it. You may need to add a dependency on lagom-javadsl-kafka-broker to your projects dependencies.")
        case _ => throw new IllegalStateException("Method " + method + " is not described by the service client descriptor")
      }
    }
  }
}

private class JavadslServiceCallInvocationHandler[Request, Response](ws: WSClient, webSocketClient: JavadslWebSocketClient,
                                                                     serviceInfo: ServiceInfo, serviceLocator: ServiceLocator,
                                                                     descriptor: Descriptor, endpoint: Call[Request, Response], holder: MethodServiceCallHolder)(implicit ec: ExecutionContext, mat: Materializer) {
  private val pathSpec = JavadslPath.fromCallId(endpoint.callId)

  def invoke(args: Seq[AnyRef]): ServiceCall[Request, Response] = {
    val (path, queryParams) = pathSpec.format(holder.invoke(args))

    new JavadslClientServiceCall[Request, Response, Response](new JavadslClientServiceCallInvoker[Request, Response](ws, webSocketClient,
      serviceInfo, serviceLocator, descriptor, endpoint, path, queryParams), identity, (_, msg) => msg)
  }
}

/**
 * The service call implementation. Delegates actual work to the invoker, while maintaining the handler function for
 * the request header and a transformer function for the response.
 */
private class JavadslClientServiceCall[Request, ResponseMessage, ServiceCallResponse](
  invoker: JavadslClientServiceCallInvoker[Request, ResponseMessage], requestHeaderHandler: RequestHeader => RequestHeader,
  responseHandler: (ResponseHeader, ResponseMessage) => ServiceCallResponse
)(implicit ec: ExecutionContext) extends ServiceCall[Request, ServiceCallResponse] {

  override def invoke(request: Request): CompletionStage[ServiceCallResponse] = {
    invoker.doInvoke(request, requestHeaderHandler).map(responseHandler.tupled).toJava
  }

  override def handleRequestHeader(handler: function.Function[RequestHeader, RequestHeader]): ServiceCall[Request, ServiceCallResponse] = {
    new JavadslClientServiceCall(invoker, requestHeaderHandler.andThen(handler.apply), responseHandler)
  }

  override def handleResponseHeader[T](handler: BiFunction[ResponseHeader, ServiceCallResponse, T]): ServiceCall[Request, T] = {
    new JavadslClientServiceCall[Request, ResponseMessage, T](invoker, requestHeaderHandler,
      (header, message) => handler.apply(header, responseHandler(header, message)))
  }

  /**
   * This is overridden in an attempt to try and provide better error reporting for when the request is not a unit type.
   */
  override def invoke(): CompletionStage[ServiceCallResponse] = {
    if (invoker.call.requestSerializer() != MessageSerializers.NOT_USED) {
      throw new UnsupportedOperationException("Invocation without a request message may only be done when the request message is NotUsed. Use invoke(Id, Request) instead.")
    } else {
      invoke(NotUsed.asInstanceOf[Request])
    }
  }
}

private class JavadslClientServiceCallInvoker[Request, Response](
  ws: WSClient, webSocketClient: JavadslWebSocketClient, serviceInfo: ServiceInfo, override val serviceLocator: ServiceLocator,
  override val descriptor: Descriptor, override val call: Call[Request, Response], path: String, queryParams: Map[String, Seq[String]]
)(implicit ec: ExecutionContext, mat: Materializer) extends ClientServiceCallInvoker[Request, Response](ws, serviceInfo.serviceName(), path, queryParams) with JavadslServiceApiBridge {

  override protected def doMakeStreamedCall(requestStream: Source[ByteString, _], requestSerializer: MessageSerializer.NegotiatedSerializer[_, _],
                                            requestHeader: RequestHeader): Future[(ResponseHeader, Source[ByteString, _])] = {
    webSocketClient.connect(descriptor.exceptionSerializer, WebSocketVersion.V13, requestHeader, requestStream)
  }

}

case object NoTopicFactory
