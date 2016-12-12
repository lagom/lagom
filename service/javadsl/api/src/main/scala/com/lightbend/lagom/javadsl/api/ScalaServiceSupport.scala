/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api

import java.lang.reflect.Method

import com.lightbend.lagom.javadsl.api.Descriptor.Call

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.macros.blackbox.Context
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.broker.Topic

/**
 * Support for implementing javadsl service calls with Scala.
 */
object ScalaService {

  import ScalaServiceSupport.ScalaMethodCall

  def call[Request, Response](method: ScalaMethodCall[ServiceCall[Request, Response]]): Call[Request, Response] =
    Service.call[Request, Response](method.method)
  def namedCall[Request, Response](name: String, method: ScalaMethodCall[ServiceCall[Request, Response]]): Call[Request, Response] =
    Service.namedCall[Request, Response](name, method.method)
  def pathCall[Request, Response](path: String, method: ScalaMethodCall[ServiceCall[Request, Response]]): Call[Request, Response] =
    Service.pathCall[Request, Response](path, method.method)
  def restCall[Request, Response](restMethod: transport.Method, path: String, method: ScalaMethodCall[ServiceCall[Request, Response]]): Call[Request, Response] =
    Service.restCall[Request, Response](restMethod, path, method.method)
  def topic[Message](topicId: String, method: ScalaMethodCall[Topic[Message]]): TopicCall[Message] =
    Service.topic[Message](topicId, method.method)

  def named(name: String): Descriptor = Service.named(name)
}

object ScalaServiceSupport {

  final class ScalaMethodCall[T](val method: Method)
  object ScalaMethodCall {
    implicit def methodFor[T](f: () => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: _ => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
    implicit def methodFor[T](f: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => T): ScalaMethodCall[T] = macro methodForImpl[T]
  }

  def getMethodWithName[T](clazz: Class[_], name: String): ScalaMethodCall[T] = {
    new ScalaMethodCall[T](clazz.getMethods.find(_.getName == name).getOrElse(throw new NoSuchMethodException(name)))
  }

  def methodForImpl[T](c: Context)(f: c.Expr[Any])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = {
    import c.universe._

    f match {
      case Expr(Block((_, Function(_, Apply(Select(This(thisType), TermName(methodName)), _))))) =>
        val methodNameString = Literal(Constant(methodName))
        c.Expr[ScalaMethodCall[T]](q"_root_.com.lightbend.lagom.javadsl.api.ScalaServiceSupport.getMethodWithName[${tType.tpe}](_root_.scala.Predef.classOf[$thisType], $methodNameString)")
      case other =>
        c.abort(c.enclosingPosition, "methodFor must only be invoked with a reference to a function on this, for example, methodFor(this.someFunction)")
    }
  }
}
