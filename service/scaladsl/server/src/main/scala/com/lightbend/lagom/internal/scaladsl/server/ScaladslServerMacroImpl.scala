/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.server

import com.lightbend.lagom.internal.scaladsl.client.ScaladslClientMacroImpl
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service }
import com.lightbend.lagom.scaladsl.server.{ LagomServer, LagomServiceBinder }

import scala.reflect.macros.blackbox

private[lagom] class ScaladslServerMacroImpl(override val c: blackbox.Context) extends ScaladslClientMacroImpl(c) {
  import c.universe._

  val server = q"_root_.com.lightbend.lagom.scaladsl.server"

  def simpleBind[T <: Service](serviceFactory: Tree)(implicit serviceType: WeakTypeTag[T]): Expr[LagomServer] = {
    val binder = createBinder[T]
    c.Expr[LagomServer](q"""{
      $server.LagomServer.forService(
        $binder.to($serviceFactory)
      )
    }
    """)
  }

  /**
   * Creates the binder for the service.
   */
  def createBinder[T <: Service](implicit serviceType: WeakTypeTag[T]): Expr[LagomServiceBinder[T]] = {
    val descriptor = readDescriptor[T]
    c.Expr[LagomServiceBinder[T]](q"""
      $server.LagomServiceBinder[${weakTypeOf[T]}](lagomServerBuilder, $descriptor)
    """)
  }

  /**
   * This macro provides a dummy implementation of the service so that it can read the service descriptor.
   */
  def readDescriptor[T <: Service](implicit serviceType: WeakTypeTag[T]): Expr[Descriptor] = {
    val extracted = validateServiceInterface[T](serviceType)

    val serviceMethodImpls: Seq[Tree] = (extracted.serviceCalls ++ extracted.topics).map { serviceMethod =>
      val methodParams = serviceMethod.paramLists.map { paramList =>
        paramList.map(param => q"${param.name.toTermName}: ${param.typeSignature}")
      }

      q"""
        override def ${serviceMethod.name}(...$methodParams) = {
          throw new _root_.scala.NotImplementedError("Service methods and topics must not be invoked from service trait")
        }
      """
    } match {
      case Seq() => Seq(EmptyTree)
      case s     => s
    }

    c.Expr[Descriptor](q"""
      new ${serviceType.tpe} {
        ..$serviceMethodImpls
      }.descriptor
    """)
  }

}
