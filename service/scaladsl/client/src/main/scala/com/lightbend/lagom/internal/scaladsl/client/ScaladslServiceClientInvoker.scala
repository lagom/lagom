/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.internal.api.Path
import com.lightbend.lagom.internal.client.ClientServiceCallInvoker
import com.lightbend.lagom.internal.scaladsl.api.ScaladslPath
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactory
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.Descriptor.{ Call, NamedCallId, RestCallId, TopicCall }
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceSupport.{ ScalaMethodServiceCall, ScalaMethodTopic }
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.deser._
import com.lightbend.lagom.scaladsl.api.transport.{ Method, RequestHeader, ResponseHeader }
import com.lightbend.lagom.scaladsl.client.{ ServiceClientConstructor, ServiceClientContext, ServiceClientImplementationContext, ServiceResolver }
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import play.api.libs.ws.WSClient

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

private[lagom] class ScaladslServiceClient(ws: WSClient, webSocketClient: ScaladslWebSocketClient, grpcChannelPool: GrpcChannelPool, serviceInfo: ServiceInfo,
                                           serviceLocator: ServiceLocator, serviceResolver: ServiceResolver,
                                           topicFactory: Option[TopicFactory])(implicit ec: ExecutionContext, mat: Materializer) extends ServiceClientConstructor {

  private val ctx: ServiceClientImplementationContext = new ServiceClientImplementationContext {
    override def resolve(unresolvedDescriptor: Descriptor): ServiceClientContext = {
      val descriptor = serviceResolver.resolve(unresolvedDescriptor)
      descriptor.serviceCallTransports.head match {
        case ServiceCallTransport.Grpc =>
          new GrpcServiceClientContext(grpcChannelPool, serviceLocator, topicFactory, descriptor)
        case ServiceCallTransport.Http =>
          new HttpServiceClientContext(ws, webSocketClient, serviceInfo, serviceLocator, topicFactory, descriptor)
      }

    }
  }

  override def construct[S <: Service](constructor: (ServiceClientImplementationContext) => S): S = constructor(ctx)
}

private trait OptionalTopicFactoryServiceClientContext extends ServiceClientContext {
  val topicFactory: Option[TopicFactory]
  val descriptor: Descriptor

  val topics: Map[String, TopicCall[_]] = descriptor.topics.map { topic =>
    topic.topicHolder match {
      case methodTopic: ScalaMethodTopic[_] =>
        methodTopic.method.getName -> topic
    }
  }.toMap

  override def createTopic[Message](methodName: String): Topic[Message] = {
    topicFactory match {
      case Some(tf) =>
        topics.get(methodName) match {
          case Some(topicCall: TopicCall[Message]) => tf.create(topicCall)
          case None                                => throw new RuntimeException("No descriptor for topic method: " + methodName)
        }
      case None =>
        throw new RuntimeException("No message broker implementation to create topic from. Did you forget to include com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaClientComponents in your application?")
    }
  }
}

private class GrpcServiceClientContext(grpcChannelPool: GrpcChannelPool, serviceLocator: ServiceLocator,
                                       val topicFactory: Option[TopicFactory], val descriptor: Descriptor)(implicit executionContext: ExecutionContext) extends ServiceClientContext with OptionalTopicFactoryServiceClientContext {

  private val serviceCalls: Map[String, ServiceCall[_, _]] = descriptor.calls.map { call =>
    call.callId match {
      case named: NamedCallId =>
        call.serviceCallHolder match {
          case methodServiceCall: ScalaMethodServiceCall[Any, Any] if methodServiceCall.pathParamSerializers.isEmpty =>
            methodServiceCall.method.getName -> new GrpcServiceCall[Any, Any, Any](descriptor, call.asInstanceOf[Call[Any, Any]], grpcChannelPool,
              serviceLocator, identity, (h, m) => m)
          case other =>
            throw new RuntimeException("Tried to bind GRPC client for service call with method parameters. GRPC does not support method parameter service calls.")
        }
      case other =>
        throw new RuntimeException("Tried to bind GRPC client for service call with a path or rest call ID. GRPC only supports named call ids.")
    }
  }.toMap

  override def createServiceCall[Request, Response](methodName: String, params: immutable.Seq[Any]): ServiceCall[Request, Response] = {
    serviceCalls.get(methodName) match {
      case Some(serviceCall: ServiceCall[Request, Response]) => serviceCall
      case None => throw new RuntimeException("No descriptor found for service call method: " + methodName)
    }
  }
}

private class HttpServiceClientContext(ws: WSClient, webSocketClient: ScaladslWebSocketClient, serviceInfo: ServiceInfo,
                                       serviceLocator: ServiceLocator, val topicFactory: Option[TopicFactory], val descriptor: Descriptor)(implicit ec: ExecutionContext, mat: Materializer) extends ServiceClientContext with OptionalTopicFactoryServiceClientContext {

  private val serviceCalls: Map[String, ScalaServiceCall] = descriptor.calls.map { call =>
    call.serviceCallHolder match {
      case methodServiceCall: ScalaMethodServiceCall[_, _] =>
        val pathSpec = ScaladslPath.fromCallId(call.callId)
        methodServiceCall.method.getName -> ScalaServiceCall(call, pathSpec, methodServiceCall.pathParamSerializers)
    }
  }.toMap

  override def createServiceCall[Request, Response](methodName: String, params: immutable.Seq[Any]): ServiceCall[Request, Response] = {
    serviceCalls.get(methodName) match {
      case Some(ScalaServiceCall(call, pathSpec, pathParamSerializers)) =>
        val serializedParams = pathParamSerializers.zip(params).map {
          case (serializer: PathParamSerializer[Any], param) => serializer.serialize(param)
        }
        val (path, queryParams) = pathSpec.format(serializedParams)

        val invoker = new ScaladslClientServiceCallInvoker[Request, Response](ws, webSocketClient, serviceInfo,
          serviceLocator, descriptor, call.asInstanceOf[Call[Request, Response]], path, queryParams)

        new ScaladslClientServiceCall[Request, Response, Response](invoker, identity, (header, message) => message)

      case None => throw new RuntimeException("No descriptor for service call method: " + methodName)
    }
  }

  private case class ScalaServiceCall(call: Call[_, _], pathSpec: Path, pathParamSerializers: immutable.Seq[PathParamSerializer[_]])
}

/**
 * The service call implementation. Delegates actual work to the invoker, while maintaining the handler function for
 * the request header and a transformer function for the response.
 */
private class ScaladslClientServiceCall[Request, ResponseMessage, ServiceCallResponse](
  invoker: ScaladslClientServiceCallInvoker[Request, ResponseMessage], requestHeaderHandler: RequestHeader => RequestHeader,
  responseHandler: (ResponseHeader, ResponseMessage) => ServiceCallResponse
)(implicit ec: ExecutionContext) extends ServiceCall[Request, ServiceCallResponse] {

  override def invoke(request: Request): Future[ServiceCallResponse] = {
    invoker.doInvoke(request, requestHeaderHandler).map(responseHandler.tupled)
  }

  override def handleRequestHeader(handler: RequestHeader => RequestHeader): ServiceCall[Request, ServiceCallResponse] = {
    new ScaladslClientServiceCall(invoker, requestHeaderHandler.andThen(handler), responseHandler)
  }

  override def handleResponseHeader[T](handler: (ResponseHeader, ServiceCallResponse) => T): ServiceCall[Request, T] = {
    new ScaladslClientServiceCall[Request, ResponseMessage, T](invoker, requestHeaderHandler,
      (header, message) => handler.apply(header, responseHandler(header, message)))
  }
}

private class ScaladslClientServiceCallInvoker[Request, Response](
  ws: WSClient, webSocketClient: ScaladslWebSocketClient, serviceInfo: ServiceInfo, override val serviceLocator: ServiceLocator,
  override val descriptor: Descriptor, override val call: Call[Request, Response], path: String, queryParams: Map[String, Seq[String]]
)(implicit ec: ExecutionContext, mat: Materializer) extends ClientServiceCallInvoker[Request, Response](ws, serviceInfo.serviceName, path, queryParams) with ScaladslServiceApiBridge {

  override protected def doMakeStreamedCall(
    requestStream:     Source[ByteString, NotUsed],
    requestSerializer: NegotiatedSerializer[_, _], requestHeader: RequestHeader
  ): Future[(ResponseHeader, Source[ByteString, NotUsed])] =
    webSocketClient.connect(descriptor.exceptionSerializer, WebSocketVersion.V13, requestHeader, requestStream)
}

private[lagom] class ScaladslServiceResolver(defaultExceptionSerializer: ExceptionSerializer) extends ServiceResolver {
  override def resolve(descriptor: Descriptor): Descriptor = {
    val withExceptionSerializer: Descriptor = if (descriptor.exceptionSerializer == DefaultExceptionSerializer.Unresolved) {
      descriptor.withExceptionSerializer(defaultExceptionSerializer)
    } else descriptor

    val withAcls: Descriptor = {
      val acls = descriptor.calls.collect {
        case callWithAutoAcl if callWithAutoAcl.autoAcl.getOrElse(descriptor.autoAcl) =>
          val pathSpec = ScaladslPath.fromCallId(callWithAutoAcl.callId).regex.regex
          val method = calculateMethod(callWithAutoAcl)
          ServiceAcl(Some(method), Some(pathSpec))
      }

      if (acls.nonEmpty) {
        withExceptionSerializer.addAcls(acls: _*)
      } else withExceptionSerializer
    }

    val withCircuitBreakers = {
      // iterate all calls and replace those where CB is None with their setup or the default.
      val callsWithCircuitBreakers: Seq[Call[_, _]] = descriptor.calls.map { call =>
        val circuitBreaker = call.circuitBreaker.getOrElse(descriptor.circuitBreaker)
        call.withCircuitBreaker(circuitBreaker)
      }
      withAcls.withCalls(callsWithCircuitBreakers: _*)
    }

    withCircuitBreakers
  }

  private def calculateMethod(serviceCall: Descriptor.Call[_, _]): Method = {
    serviceCall.callId match {
      case rest: RestCallId => rest.method
      case _ =>
        // If either the request or the response serializers are streamed, then WebSockets will be used, in which case
        // the method must be GET
        if (serviceCall.requestSerializer.isStreamed || serviceCall.responseSerializer.isStreamed) {
          Method.GET
          // Otherwise, if the request serializer is used, we default to POST
        } else if (serviceCall.requestSerializer.isUsed) {
          Method.POST
        } else {
          // And if not, to GET
          Method.GET
        }
    }
  }

}
