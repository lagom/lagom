/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import java.net.URI

import com.lightbend.lagom.internal.client.CircuitBreakers
import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.{ CircuitBreaker, Descriptor, ServiceLocator }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Abstract service locator that provides circuit breaking.
 *
 * Generally, only the [[ServiceLocator.locate()]] method needs to be implemented, however
 * [[doWithServiceImpl()]] can be overridden if the service locator wants to
 * handle failures in some way.
 */
abstract class CircuitBreakingServiceLocator(circuitBreakers: CircuitBreakers)(implicit ec: ExecutionContext) extends ServiceLocator {

  /**
   * Do the given block with the given service looked up.
   *
   * This is invoked by [[doWithService()]], after wrapping the passed in block
   * in a circuit breaker if configured to do so.
   *
   * The default implementation just delegates to the [[locate()]] method, but this method
   * can be overridden if the service locator wants to inject other behaviour after the service call is complete.
   *
   * @param name        The service name.
   * @param serviceCall The service call that needs the service lookup.
   * @param block       A block of code that will use the looked up service, typically, to make a call on that service.
   * @return A future of the result of the block, if the service lookup was successful.
   */
  protected def doWithServiceImpl[T](name: String, serviceCall: Descriptor.Call[_, _])(block: URI => Future[T]): Future[Option[T]] = {
    locate(name, serviceCall).flatMap {
      case (Some(uri)) => block(uri).map(Some.apply)
      case None        => Future.successful(None)
    }
  }

  override final def doWithService[T](name: String, serviceCall: Call[_, _])(block: (URI) => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
    serviceCall.circuitBreaker.filter(_ != CircuitBreaker.None).map { cb =>
      val circuitBreakerId = cb match {
        case cbid: CircuitBreaker.CircuitBreakerId => cbid.id
        case _                                     => name
      }

      doWithServiceImpl(name, serviceCall) { uri =>
        circuitBreakers.withCircuitBreaker(circuitBreakerId)(block(uri))
      }
    }.getOrElse {
      doWithServiceImpl(name, serviceCall)(block)
    }
  }
}
