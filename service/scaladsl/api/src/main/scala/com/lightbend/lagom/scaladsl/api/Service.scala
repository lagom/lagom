/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import java.lang.reflect.Method

import com.lightbend.lagom.scaladsl.api.Descriptor.ServiceCallHolder
import com.lightbend.lagom.scaladsl.api.deser.{ MessageSerializer, PathParamSerializer }

import scala.collection.immutable
import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros
import scala.language.implicitConversions

trait Service {

  def descriptor: Descriptor
}

object Service {

  import ServiceSupport._
  import Descriptor._

  def named(name: String): Descriptor = Descriptor(name)

  def call[Request, Response](method: ScalaMethodCall[ServiceCall[Request, Response]])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] =
    namedCall(method.method.getName, method)

  def namedCall[Request, Response](name: String, method: ScalaMethodCall[ServiceCall[Request, Response]])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] = {
    CallImpl(NamedCallId(name), method, requestSerializer, responseSerializer)
  }

  def pathCall[Request, Response](pathPattern: String, method: ScalaMethodCall[ServiceCall[Request, Response]])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] = {
    CallImpl(PathCallId(pathPattern), method, requestSerializer, responseSerializer)
  }

  def restCall[Request, Response](method: com.lightbend.lagom.scaladsl.api.transport.Method, pathPattern: String, scalaMethod: ScalaMethodCall[ServiceCall[Request, Response]])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] = {
    CallImpl(RestCallId(method, pathPattern), scalaMethod, requestSerializer, responseSerializer)
  }
}

object ServiceSupport {
  import com.lightbend.lagom.scaladsl.api.deser.{ PathParamSerializer => PPS }

  class ScalaMethodCall[T] private[lagom] (val method: Method, val pathParamSerializers: immutable.Seq[PathParamSerializer[_]]) extends ServiceCallHolder {
    def invoke(service: Any, args: immutable.Seq[AnyRef]) = method.invoke(service, args: _*)
  }

  object ScalaMethodCall {

    implicit def methodFor0[T](f: () => T): ScalaMethodCall[T] = macro methodForImpl0[T]

    // Execute in REPL to generate:
    // (1 to 22).foreach { i => println(s"    implicit def methodFor$i[T" + (1 to i).map(p => s", P$p: PPS").mkString("") + "](f: (" + (1 to i).map(p => s"P$p").mkString(", ") + s") => T): ScalaMethodCall[T] = macro methodForImpl$i[T]") }
    implicit def methodFor1[T, P1: PPS](f: (P1) => T): ScalaMethodCall[T] = macro methodForImpl1[T]
    implicit def methodFor2[T, P1: PPS, P2: PPS](f: (P1, P2) => T): ScalaMethodCall[T] = macro methodForImpl2[T]
    implicit def methodFor3[T, P1: PPS, P2: PPS, P3: PPS](f: (P1, P2, P3) => T): ScalaMethodCall[T] = macro methodForImpl3[T]
    implicit def methodFor4[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS](f: (P1, P2, P3, P4) => T): ScalaMethodCall[T] = macro methodForImpl4[T]
    implicit def methodFor5[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS](f: (P1, P2, P3, P4, P5) => T): ScalaMethodCall[T] = macro methodForImpl5[T]
    implicit def methodFor6[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS](f: (P1, P2, P3, P4, P5, P6) => T): ScalaMethodCall[T] = macro methodForImpl6[T]
    implicit def methodFor7[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS](f: (P1, P2, P3, P4, P5, P6, P7) => T): ScalaMethodCall[T] = macro methodForImpl7[T]
    implicit def methodFor8[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8) => T): ScalaMethodCall[T] = macro methodForImpl8[T]
    implicit def methodFor9[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9) => T): ScalaMethodCall[T] = macro methodForImpl9[T]
    implicit def methodFor10[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) => T): ScalaMethodCall[T] = macro methodForImpl10[T]
    implicit def methodFor11[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) => T): ScalaMethodCall[T] = macro methodForImpl11[T]
    implicit def methodFor12[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) => T): ScalaMethodCall[T] = macro methodForImpl12[T]
    implicit def methodFor13[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) => T): ScalaMethodCall[T] = macro methodForImpl13[T]
    implicit def methodFor14[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) => T): ScalaMethodCall[T] = macro methodForImpl14[T]
    implicit def methodFor15[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) => T): ScalaMethodCall[T] = macro methodForImpl15[T]
    implicit def methodFor16[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) => T): ScalaMethodCall[T] = macro methodForImpl16[T]
    implicit def methodFor17[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) => T): ScalaMethodCall[T] = macro methodForImpl17[T]
    implicit def methodFor18[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) => T): ScalaMethodCall[T] = macro methodForImpl18[T]
    implicit def methodFor19[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) => T): ScalaMethodCall[T] = macro methodForImpl19[T]
    implicit def methodFor20[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS, P20: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) => T): ScalaMethodCall[T] = macro methodForImpl20[T]
    implicit def methodFor21[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS, P20: PPS, P21: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) => T): ScalaMethodCall[T] = macro methodForImpl21[T]
    implicit def methodFor22[T, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS, P20: PPS, P21: PPS, P22: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) => T): ScalaMethodCall[T] = macro methodForImpl22[T]
  }

  def getMethodWithName[T](clazz: Class[_], name: String, pathParamSerializers: Seq[PathParamSerializer[_]]): ScalaMethodCall[T] = {
    new ScalaMethodCall[T](
      clazz.getMethods.find(_.getName == name).getOrElse(throw new NoSuchMethodException(name)),
      pathParamSerializers.to[immutable.Seq]
    )
  }

  def methodForImpl0[T](c: Context)(f: c.Expr[Any])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f)

  // Execute in REPL to generate:
  // (1 to 22).foreach { i => println(s"  def methodForImpl$i[T](c: Context)(f: c.Expr[Any])(" + (1 to i).map(p => s"p$p: c.Expr[PPS[_]]").mkString(", ") + s")(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f" + (1 to i).map(p => s", p$p").mkString("") + ")") }
  def methodForImpl1[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1)
  def methodForImpl2[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2)
  def methodForImpl3[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3)
  def methodForImpl4[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4)
  def methodForImpl5[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5)
  def methodForImpl6[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6)
  def methodForImpl7[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7)
  def methodForImpl8[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8)
  def methodForImpl9[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9)
  def methodForImpl10[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10)
  def methodForImpl11[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11)
  def methodForImpl12[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12)
  def methodForImpl13[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13)
  def methodForImpl14[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14)
  def methodForImpl15[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15)
  def methodForImpl16[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16)
  def methodForImpl17[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17)
  def methodForImpl18[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18)
  def methodForImpl19[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19)
  def methodForImpl20[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]], p20: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20)
  def methodForImpl21[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]], p20: c.Expr[PPS[_]], p21: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21)
  def methodForImpl22[T](c: Context)(f: c.Expr[Any])(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]], p20: c.Expr[PPS[_]], p21: c.Expr[PPS[_]], p22: c.Expr[PPS[_]])(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = methodForImpl[T](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21, p22)

  private def methodForImpl[T](c: Context)(f: c.Expr[Any], ps: c.Expr[PathParamSerializer[_]]*)(implicit tType: c.WeakTypeTag[T]): c.Expr[ScalaMethodCall[T]] = {
    import c.universe._

    val pathParamSerializers = c.Expr[Seq[PathParamSerializer[_]]](
      Apply(
        Select(reify(Seq).tree, TermName("apply")),
        ps.map(_.tree).toList
      )
    )

    val (thisType, methodName) = f match {
      // This handles functions with parameter lists (including an empty parameter list)
      case Expr(Block((_, Function(_, Apply(Select(This(tt), TermName(tn)), _))))) => (tt, tn)
      // This handles functions without parameter lists
      case Expr(Function(_, Select(This(tt), TermName(tn))))                       => (tt, tn)
      case other =>
        c.abort(c.enclosingPosition, "methodFor must only be invoked with a reference to a function on this, for example, methodFor(this.someFunction)")
    }

    val methodNameString = Literal(Constant(methodName))

    c.Expr[ScalaMethodCall[T]](q"_root_.com.lightbend.lagom.scaladsl.api.ServiceSupport.getMethodWithName[${tType.tpe}](classOf[$thisType], $methodNameString, $pathParamSerializers)")
  }

}
