/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import com.lightbend.lagom.scaladsl.api.{ Service, ServiceCall }

import scala.reflect.macros.blackbox.Context

private[lagom] object ScaladslClientMacroImpl {

  /**
   * Do validation and extract the service call methods.
   *
   * todo also extract topic methods
   */
  def validateServiceInterface[T <: Service](c: Context)(implicit serviceType: c.WeakTypeTag[T]): Seq[c.universe.MethodSymbol] = {
    import c.universe._

    val serviceCallType = c.mirror.typeOf[ServiceCall[_, _]].erasure

    val serviceMethods = serviceType.tpe.members.collect {
      case method if method.isAbstract && method.isMethod => method.asMethod
    }

    val serviceCallMethods = serviceMethods.collect {
      case serviceCall if serviceCall.returnType.erasure =:= serviceCallType => serviceCall
    }

    // Check that descriptor is not abstract
    if (serviceMethods.exists(m => m.name.decodedName.toString == "descriptor" && m.paramLists.isEmpty)) {
      c.abort(c.enclosingPosition, s"${serviceType.tpe}.descriptor must be implemented in order to generate a Lagom client.")
    }

    // Make sure there are no overloaded abstract methods. This limitation is due to us only looking up service calls
    // by method name, and could be removed in the future.
    val duplicates = serviceMethods.groupBy(_.name.decodedName.toString).mapValues(_.toSeq).filter(_._2.size > 1)
    if (duplicates.nonEmpty) {
      c.abort(c.enclosingPosition, "Overloaded service methods are not allowed on a Lagom client, overloaded methods are: " + duplicates.keys.mkString(", "))
    }

    // Validate that all the abstract methods are service call methods or topic methods
    val nonServiceCallOrTopicMethods = serviceMethods.toSet -- serviceCallMethods
    if (nonServiceCallOrTopicMethods.nonEmpty) {
      c.abort(c.enclosingPosition, s"Can't generate a Lagom client for ${serviceType.tpe} since the following abstract methods don't return service calls or topics:${nonServiceCallOrTopicMethods.map(_.name).mkString("\n", "\n", "")}")
    }

    serviceCallMethods.toSeq
  }

  def implementClient[T <: Service](c: Context)(implicit serviceType: c.WeakTypeTag[T]): c.Expr[T] = {

    import c.universe._

    val scaladsl = q"_root_.com.lightbend.lagom.scaladsl"
    val client = q"$scaladsl.client"

    val serviceCallMethods = validateServiceInterface[T](c)

    // Extract the target that "implement" was invoked on, so we can invoke "doImplement" on it instead
    val serviceClient = c.macroApplication match {
      case TypeApply(Select(clientTarget, TermName("implement")), _) => clientTarget
      case other => c.abort(c.enclosingPosition, "Don't know how to find the service client from tree: " + c.macroApplication)
    }

    val implementationContext = TermName(c.freshName("implementationContext"))
    val serviceContext = TermName(c.freshName("serviceContext"))

    val serviceMethodImpls = serviceCallMethods.map { serviceMethod =>
      val methodParams = serviceMethod.paramLists.map { paramList =>
        paramList.map(param => q"${param.name.toTermName}: ${param.typeSignature}")
      }
      val methodParamNames = serviceMethod.paramLists.flatten.map(_.name)

      q"""
        override def ${serviceMethod.name}(...$methodParams) = {
          $serviceContext.createServiceCall(${serviceMethod.name.decodedName.toString},
            _root_.scala.collection.immutable.Seq(..$methodParamNames))
        }
      """
    }

    c.Expr[T](q"""
      $serviceClient match {
        case serviceClientConstructor: $client.ServiceClientConstructor =>
          serviceClientConstructor.construct(($implementationContext: $client.ServiceClientImplementationContext) => new ${serviceType.tpe} {
            private val $serviceContext: $client.ServiceClientContext = $implementationContext.resolve(this.descriptor)

            ..$serviceMethodImpls
          })
        case other => throw new _root_.java.lang.RuntimeException(${serviceClient.toString} + " of type " + $serviceClient.getClass.getName + " does not implement ServiceClientConstructor")
      }
    """)
  }

}
