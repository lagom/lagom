/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.client

import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.{ Service, ServiceCall }

import scala.reflect.macros.blackbox

private[lagom] object ScaladslClientMacroImpl {
  final case class ExtractedMethods[MethodSymbol](serviceCalls: Seq[MethodSymbol], topics: Seq[MethodSymbol])
}

private[lagom] class ScaladslClientMacroImpl(val c: blackbox.Context) {
  import ScaladslClientMacroImpl._
  import c.universe._

  val client = q"_root_.com.lightbend.lagom.scaladsl.client"

  /**
   * Do validation and extract the service call methods.
   */
  def validateServiceInterface[T <: Service](implicit serviceType: WeakTypeTag[T]): ExtractedMethods[MethodSymbol] = {
    val serviceCallType = c.mirror.typeOf[ServiceCall[_, _]].erasure
    val topicType = c.mirror.typeOf[Topic[_]].erasure

    val serviceMethods = serviceType.tpe.members.collect {
      case method if method.isAbstract && method.isMethod => method.asMethod
    }

    val serviceCallMethods = serviceMethods.collect {
      case serviceCall if serviceCall.returnType.erasure =:= serviceCallType => serviceCall
    }

    val topicMethods = serviceMethods.collect {
      case topic if topic.returnType.erasure =:= topicType => topic
    }

    // Check that descriptor is not abstract
    if (serviceMethods.exists(m => m.name.decodedName.toString == "descriptor" && m.paramLists.isEmpty)) {
      abort(s"${serviceType.tpe}.descriptor must be implemented in order to generate a Lagom client.")
    }

    // Make sure there are no overloaded abstract methods. This limitation is due to us only looking up service calls
    // by method name, and could be removed in the future.
    val duplicates = serviceMethods.groupBy(_.name.decodedName.toString).mapValues(_.toSeq).filter(_._2.size > 1)
    if (duplicates.nonEmpty) {
      abort("Overloaded service methods are not allowed on a Lagom client, overloaded methods are: " + duplicates.keys.mkString(", "))
    }

    // Validate that all the abstract methods are service call methods or topic methods
    val nonServiceCallOrTopicMethods = serviceMethods.toSet -- serviceCallMethods -- topicMethods
    if (nonServiceCallOrTopicMethods.nonEmpty) {
      abort(s"Can't generate a Lagom client for ${serviceType.tpe} since the following abstract methods don't return service calls or topics:${nonServiceCallOrTopicMethods.map(_.name).mkString("\n", "\n", "")}")
    }

    // Validate that all topics have zero parameters
    topicMethods.foreach { topic =>
      if (topic.paramLists.flatten.nonEmpty) {
        abort(s"Topic methods must have zero parameters")
      }
    }

    ExtractedMethods(serviceCallMethods.toSeq, topicMethods.toSeq)
  }

  def implementClient[T <: Service](implicit serviceType: WeakTypeTag[T]): Expr[T] = {
    val extractedMethods = validateServiceInterface[T]

    // Extract the target that "implement" was invoked on, so we can invoke "doImplement" on it instead
    val serviceClient = c.macroApplication match {
      case TypeApply(Select(clientTarget, TermName("implement")), _) => clientTarget
      case _ => abort("Don't know how to find the service client from tree: " + c.macroApplication)
    }

    val implementationContext = TermName(c.freshName("implementationContext"))
    val serviceContext = TermName(c.freshName("serviceContext"))

    def createMethodParams(method: MethodSymbol) = method.paramLists.map { paramList =>
      paramList.map(param => q"${param.name.toTermName}: ${param.typeSignature}")
    }

    val serviceMethodImpls = extractedMethods.serviceCalls.map { serviceMethod =>
      val methodParams = createMethodParams(serviceMethod)
      val methodParamNames = serviceMethod.paramLists.flatten.map(_.name)

      q"""
        override def ${serviceMethod.name}(...$methodParams) = {
          $serviceContext.createServiceCall(${serviceMethod.name.decodedName.toString},
            _root_.scala.collection.immutable.Seq[_root_.scala.Any](..$methodParamNames))
        }
      """
    }

    val topicMethodImpls = extractedMethods.topics.map { topicMethod =>
      val methodParams = createMethodParams(topicMethod)

      q"""
        override def ${topicMethod.name}(...$methodParams) = {
          $serviceContext.createTopic(${topicMethod.name.decodedName.toString})
        }
       """
    }

    c.Expr[T](q"""
      $serviceClient match {
        case serviceClientConstructor: $client.ServiceClientConstructor =>
          serviceClientConstructor.construct(($implementationContext: $client.ServiceClientImplementationContext) => new ${serviceType.tpe} {
            private val $serviceContext: $client.ServiceClientContext = $implementationContext.resolve(this.descriptor)

            ..$serviceMethodImpls

            ..$topicMethodImpls
          })
        case other => throw new _root_.java.lang.RuntimeException(${serviceClient.toString} + " of type " + $serviceClient.getClass.getName + " does not implement ServiceClientConstructor")
      }
    """)
  }

  private def abort(msg: String): Nothing = c.abort(c.enclosingPosition, msg)
}
