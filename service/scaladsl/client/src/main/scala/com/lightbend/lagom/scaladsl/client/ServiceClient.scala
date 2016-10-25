/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.client

import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import com.lightbend.lagom.internal.scaladsl.client.ScaladslClientMacroImpl

import scala.collection.immutable
import scala.language.experimental.macros

trait ServiceClient {
  def implement[S <: Service]: S = macro ScaladslClientMacroImpl.implementClient[S]

  def doImplement[S <: Service](constructor: ServiceClientImplementationContext => S): S
}

trait ServiceClientImplementationContext {
  def resolve(descriptor: Descriptor): ServiceClientContext
}

trait ServiceClientContext {
  def createServiceCall[Request, Response](methodName: String, params: immutable.Seq[Any]): ServiceCall[Request, Response]
}
