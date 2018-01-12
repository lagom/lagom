/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.server

import akka.stream.Materializer
import akka.util.ByteString
import com.lightbend.lagom.internal.api.Path
import com.lightbend.lagom.internal.scaladsl.api.ScaladslPath
import com.lightbend.lagom.internal.scaladsl.client.ScaladslServiceApiBridge
import com.lightbend.lagom.internal.server.ServiceRouter
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Descriptor.RestCallId
import com.lightbend.lagom.scaladsl.api.ServiceSupport.ScalaMethodServiceCall
import com.lightbend.lagom.scaladsl.api.deser.StreamedMessageSerializer
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.server.{ LagomServiceRouter, PlayServiceCall }
import play.api.Logger
import play.api.http.HttpConfiguration
import play.api.mvc.EssentialAction

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

class ScaladslServiceRouter(override protected val descriptor: Descriptor, service: Any, httpConfiguration: HttpConfiguration)(implicit ec: ExecutionContext, mat: Materializer)
  extends ServiceRouter(httpConfiguration) with LagomServiceRouter with ScaladslServiceApiBridge {

  private class ScaladslServiceRoute(override val call: Call[Any, Any]) extends ServiceRoute {
    override val path: Path = ScaladslPath.fromCallId(call.callId)
    override val method: Method = call.callId match {
      case rest: RestCallId => rest.method
      case _ => if (call.requestSerializer.isUsed) {
        Method.POST
      } else {
        Method.GET
      }
    }
    override val isWebSocket: Boolean =

      messageSerializerIsStreamed(call.requestSerializer) ||
        messageSerializerIsStreamed(call.responseSerializer)

    private val holder: ScalaMethodServiceCall[Any, Any] = call.serviceCallHolder match {
      case holder: ScalaMethodServiceCall[Any, Any] => holder
    }

    override def createServiceCall(params: Seq[Seq[String]]): ServiceCall[Any, Any] = {
      val args = params.zip(holder.pathParamSerializers).map {
        case (params, serializer) => serializer.deserialize(params.to[immutable.Seq])
      }.to[immutable.Seq]

      holder.invoke(service, args.asInstanceOf[immutable.Seq[AnyRef]]).asInstanceOf[ServiceCall[Any, Any]]
    }
  }

  override protected val serviceRoutes: Seq[ServiceRoute] =
    descriptor.calls.map(call => new ScaladslServiceRoute(call.asInstanceOf[Call[Any, Any]]))

  /**
   * Create the action.
   */
  override protected def action[Request, Response](
    call:               Call[Request, Response],
    descriptor:         Descriptor,
    serviceCall:        ServiceCall[Request, Response],
    requestSerializer:  MessageSerializer[Request, ByteString],
    responseSerializer: MessageSerializer[Response, ByteString]
  ): EssentialAction = {

    serviceCall match {
      // If it's a Play service call, then rather than creating the action directly, we let it create the action, and
      // pass it a callback that allows it to convert a service call into an action.
      case playServiceCall: PlayServiceCall[Request, Response] =>
        playServiceCall.invoke(serviceCall => createAction(call, descriptor, serviceCall, requestSerializer, responseSerializer))
      case _ =>
        createAction(call, descriptor, serviceCall, requestSerializer, responseSerializer)
    }
  }

  override protected def maybeLogException(exc: Throwable, log: => Logger, call: Call[_, _]) = {
    exc match {
      case _: NotFound | _: Forbidden => // no logging
      case e @ (_: UnsupportedMediaType | _: PayloadTooLarge | _: NotAcceptable) =>
        log.warn(e.getMessage)
      case e =>
        log.error(s"Exception in ${call.callId}", e)
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
        serviceCall.handleRequestHeader(_ => requestHeader).handleResponseHeader(_ -> _).invoke(request)
    }
  }

}
