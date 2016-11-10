/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.server

import com.lightbend.lagom.internal.scaladsl.client.ScaladslClientMacroImpl
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.server.LagomServiceBinder

import scala.reflect.macros.blackbox.Context

private[lagom] object ScaladslServerMacroImpl {

  /**
   * This macro provides a dummy implementation of the service so that it can read the service descriptor. This
   * allows the service name to be extracted without having to instantiate the service, avoiding circular dependencies.
   */
  def createBinder[T <: Service](c: Context)(implicit serviceType: c.WeakTypeTag[T]): c.Expr[LagomServiceBinder[T]] = {

    import c.universe._

    val scaladsl = q"_root_.com.lightbend.lagom.scaladsl"
    val server = q"$scaladsl.server"

    val extracted = ScaladslClientMacroImpl.validateServiceInterface[T](c)

    val serviceMethodImpls = (extracted.serviceCalls ++ extracted.topics).map { serviceMethod =>
      val methodParams = serviceMethod.paramLists.map { paramList =>
        paramList.map(param => q"${param.name.toTermName}: ${param.typeSignature}")
      }

      q"""
        override def ${serviceMethod.name}(...$methodParams) = {
          throw new _root_.scala.NotImplementedError("Service methods and topics must not be invoked from service trait")
        }
      """
    }

    c.Expr[LagomServiceBinder[T]](q"""
      $server.LagomServiceBinder(lagomServerBuilder, new ${serviceType.tpe} {
        ..$serviceMethodImpls
      }.descriptor)
    """)
  }

}
