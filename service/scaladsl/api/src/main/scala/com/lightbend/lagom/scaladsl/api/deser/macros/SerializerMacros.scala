/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api.deser.macros

import scala.reflect.macros.blackbox.Context

class SerializerMacros(val c: Context) {

  import c.universe._

  def anyValSerializer[T](implicit t: WeakTypeTag[T]): Tree = {
    withAnyValParam(t.tpe) { param =>
      // currently we do not need to invoke `import _root_.com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer._`
      // since we are in the same package
      q"""
         new _root_.com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer[${t.tpe}] {
           private val serializer = _root_.scala.Predef.implicitly[_root_.com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer[${param.typeSignature}]]

           override def serialize(parameter: ${t.tpe}): _root_.scala.collection.immutable.Seq[String] =
             serializer.serialize(parameter.${param.name.toTermName})

           override def deserialize(parameters: _root_.scala.collection.immutable.Seq[String]): ${t.tpe} =
             new ${t.tpe}(serializer.deserialize(parameters))
         }
       """
    }.getOrElse(fail("PathParamSerializer", t.tpe))
  }

  private def fail(enc: String, t: Type) = {
    c.abort(c.enclosingPosition, s"could not find the implicit $enc for AnyVal Type $t")
  }

  private def withAnyValParam[R](tpe: Type)(f: Symbol => R): Option[R] = {
    tpe.baseType(c.symbolOf[AnyVal]) match {
      case NoType => None
      case _ =>
        primaryConstructor(tpe).map(_.paramLists.flatten).collect {
          case param :: Nil => f(param)
        }
    }
  }

  private def primaryConstructor(t: Type) = {
    t.members.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m.typeSignature.asSeenFrom(t, t.typeSymbol)
    }
  }

}
