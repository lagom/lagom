/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
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
import com.lightbend.lagom.scaladsl.api.Descriptor.{ Call, NamedCallId, TopicCall }
import com.lightbend.lagom.scaladsl.api.ServiceSupport.{ ScalaMethodServiceCall, ScalaMethodTopic }
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.deser._
import com.lightbend.lagom.scaladsl.api.transport.{ Method, RequestHeader, ResponseHeader }
import com.lightbend.lagom.scaladsl.client.{ ServiceClientConstructor, ServiceClientContext, ServiceClientImplementationContext, ServiceResolver }
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import play.api.libs.ws.WSClient

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

private[lagom] class ScaladslServiceClient(ws: WSClient, webSocketClient: ScaladslWebSocketClient, serviceInfo: ServiceInfo,
                                           serviceLocator: ServiceLocator, serviceResolver: ServiceResolver,
                                           topicFactory: Option[TopicFactory])(implicit ec: ExecutionContext, mat: Materializer) extends ServiceClientConstructor {

  private val ctx: ServiceClientImplementationContext = new ServiceClientImplementationContext {
    override def resolve(unresolvedDescriptor: Descriptor): ServiceClientContext = new ServiceClientContext {

      val descriptor = serviceResolver.resolve(unresolvedDescriptor)

      val serviceCalls: Map[String, ScalaServiceCall] = descriptor.calls.map { call =>
        call.serviceCallHolder match {
          case methodServiceCall: ScalaMethodServiceCall[_, _] =>
            val pathSpec = ScaladslPath.fromCallId(call.callId)
            methodServiceCall.method.getName -> ScalaServiceCall(call, pathSpec, methodServiceCall.pathParamSerializers)
        }
      }.toMap

      val topics: Map[String, TopicCall[_]] = descriptor.topics.map { topic =>
        topic.topicHolder match {
          case methodTopic: ScalaMethodTopic[_] =>
            methodTopic.method.getName -> topic
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
  }

  override def construct[S <: Service](constructor: (ServiceClientImplementationContext) => S): S = constructor(ctx)

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
    val withExceptionSerializer = if (descriptor.exceptionSerializer == DefaultExceptionSerializer.Unresolved) {
      descriptor.withExceptionSerializer(defaultExceptionSerializer)
    } else descriptor

    val withAcls = {
      val acls = descriptor.calls.collect {
        case callWithAutoAcl if callWithAutoAcl.autoAcl.getOrElse(descriptor.autoAcl) =>
          val pathSpec = ScaladslPath.fromCallId(callWithAutoAcl.callId).regex.regex
          val method = methodFromSerializer(callWithAutoAcl.requestSerializer, callWithAutoAcl.responseSerializer)
          ServiceAcl(Some(method), Some(pathSpec))
      }

      if (acls.nonEmpty) {
        withExceptionSerializer.addAcls(acls: _*)
      } else withExceptionSerializer
    }

    withAcls
  }

  private def methodFromSerializer(requestSerializer: MessageSerializer[_, _], responseSerializer: MessageSerializer[_, _]) = {
    if (requestSerializer.isStreamed || responseSerializer.isStreamed) {
      Method.GET
    } else if (requestSerializer.isUsed) {
      Method.POST
    } else {
      Method.GET
    }
  }

}
