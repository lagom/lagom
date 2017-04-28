/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.server

import com.lightbend.lagom.internal.scaladsl.client.ScaladslClientMacroImpl
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service }
import com.lightbend.lagom.scaladsl.server.{ LagomServer, LagomServiceBinder }

import scala.reflect.macros.blackbox.Context

private[lagom] object ScaladslServerMacroImpl {

  def simpleBind[T <: Service](c: Context)(serviceFactory: c.Tree)(implicit serviceType: c.WeakTypeTag[T]): c.Expr[LagomServer] = {
    import c.universe._

    val scaladsl = q"_root_.com.lightbend.lagom.scaladsl"
    val server = q"$scaladsl.server"

    val descriptor = readDescriptor[T](c)
    c.Expr[LagomServer](q"""{
      $server.LagomServer.forService(
        $server.LagomServiceBinder(lagomServerBuilder, $descriptor).to($serviceFactory)
      )
    }
    """)

  }

  /**
   * Creates the binder for the service.
   */
  def createBinder[T <: Service](c: Context)(implicit serviceType: c.WeakTypeTag[T]): c.Expr[LagomServiceBinder[T]] = {

    import c.universe._

    val scaladsl = q"_root_.com.lightbend.lagom.scaladsl"
    val server = q"$scaladsl.server"

    val descriptor = readDescriptor[T](c)
    c.Expr[LagomServiceBinder[T]](q"""
      $server.LagomServiceBinder(lagomServerBuilder, $descriptor)
    """)
  }

  /**
   * This macro provides a dummy implementation of the service so that it can read the service descriptor.
   */
  def readDescriptor[T <: Service](c: Context)(implicit serviceType: c.WeakTypeTag[T]): c.Expr[Descriptor] = {

    import c.universe._

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

    c.Expr[Descriptor](q"""
      new ${serviceType.tpe} {
        ..$serviceMethodImpls
      }.descriptor
    """)
  }

}
