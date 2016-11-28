/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import java.lang.reflect.Method

import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.Descriptor.{ ServiceCallHolder, TopicHolder }
import com.lightbend.lagom.scaladsl.api.broker.Topic
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

  def call[Request, Response](method: ScalaMethodServiceCall[Request, Response])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] =
    namedCall(method.method.getName, method)

  def namedCall[Request, Response](name: String, method: ScalaMethodServiceCall[Request, Response])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] = {
    CallImpl(NamedCallId(name), method, requestSerializer, responseSerializer)
  }

  def pathCall[Request, Response](pathPattern: String, method: ScalaMethodServiceCall[Request, Response])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] = {
    CallImpl(PathCallId(pathPattern), method, requestSerializer, responseSerializer)
  }

  def restCall[Request, Response](method: com.lightbend.lagom.scaladsl.api.transport.Method, pathPattern: String, scalaMethod: ScalaMethodServiceCall[Request, Response])(implicit requestSerializer: MessageSerializer[Request, _], responseSerializer: MessageSerializer[Response, _]): Call[Request, Response] = {
    CallImpl(RestCallId(method, pathPattern), scalaMethod, requestSerializer, responseSerializer)
  }

  def topic[Message](topicId: String, method: ScalaMethodTopic[Message])(implicit messageSerializer: MessageSerializer[Message, ByteString]): TopicCall[Message] = {
    TopicCallImpl[Message](Topic.TopicId(topicId), method, messageSerializer)
  }
}

object ServiceSupport {
  import com.lightbend.lagom.scaladsl.api.deser.{ PathParamSerializer => PPS }

  class ScalaMethodTopic[Message] private[lagom] (val method: Method) extends TopicHolder

  object ScalaMethodTopic {
    implicit def topicMethodFor[Message](f: => Topic[Message]): ScalaMethodTopic[Message] = macro topicMethodForImpl[Message]
    implicit def topicMethodFor0[Message](f: () => Topic[Message]): ScalaMethodTopic[Message] = macro topicMethodForImpl[Message]
  }

  class ScalaMethodServiceCall[Request, Response] private[lagom] (val method: Method, val pathParamSerializers: immutable.Seq[PathParamSerializer[_]]) extends ServiceCallHolder {
    def invoke(service: Any, args: immutable.Seq[AnyRef]) = method.invoke(service, args: _*)
  }

  object ScalaMethodServiceCall {

    implicit def methodFor[Q, R](f: => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl0[Q, R]
    implicit def methodFor0[Q, R](f: () => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl0[Q, R]

    // Execute in REPL to generate:
    // (1 to 22).foreach { i => println(s"    implicit def methodFor$i[Q, R" + (1 to i).map(p => s", P$p: PPS").mkString("") + "](f: (" + (1 to i).map(p => s"P$p").mkString(", ") + s") => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl$i[Q, R]") }
    implicit def methodFor1[Q, R, P1: PPS](f: (P1) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl1[Q, R]
    implicit def methodFor2[Q, R, P1: PPS, P2: PPS](f: (P1, P2) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl2[Q, R]
    implicit def methodFor3[Q, R, P1: PPS, P2: PPS, P3: PPS](f: (P1, P2, P3) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl3[Q, R]
    implicit def methodFor4[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS](f: (P1, P2, P3, P4) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl4[Q, R]
    implicit def methodFor5[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS](f: (P1, P2, P3, P4, P5) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl5[Q, R]
    implicit def methodFor6[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS](f: (P1, P2, P3, P4, P5, P6) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl6[Q, R]
    implicit def methodFor7[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS](f: (P1, P2, P3, P4, P5, P6, P7) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl7[Q, R]
    implicit def methodFor8[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl8[Q, R]
    implicit def methodFor9[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl9[Q, R]
    implicit def methodFor10[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl10[Q, R]
    implicit def methodFor11[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl11[Q, R]
    implicit def methodFor12[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl12[Q, R]
    implicit def methodFor13[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl13[Q, R]
    implicit def methodFor14[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl14[Q, R]
    implicit def methodFor15[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl15[Q, R]
    implicit def methodFor16[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl16[Q, R]
    implicit def methodFor17[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl17[Q, R]
    implicit def methodFor18[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl18[Q, R]
    implicit def methodFor19[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl19[Q, R]
    implicit def methodFor20[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS, P20: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl20[Q, R]
    implicit def methodFor21[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS, P20: PPS, P21: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl21[Q, R]
    implicit def methodFor22[Q, R, P1: PPS, P2: PPS, P3: PPS, P4: PPS, P5: PPS, P6: PPS, P7: PPS, P8: PPS, P9: PPS, P10: PPS, P11: PPS, P12: PPS, P13: PPS, P14: PPS, P15: PPS, P16: PPS, P17: PPS, P18: PPS, P19: PPS, P20: PPS, P21: PPS, P22: PPS](f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) => ServiceCall[Q, R]): ScalaMethodServiceCall[Q, R] = macro methodForImpl22[Q, R]
  }

  def getServiceCallMethodWithName[Request, Response](clazz: Class[_], name: String, pathParamSerializers: Seq[PathParamSerializer[_]]): ScalaMethodServiceCall[Request, Response] = {
    new ScalaMethodServiceCall[Request, Response](
      clazz.getMethods.find(_.getName == name).getOrElse(throw new NoSuchMethodException(name)),
      pathParamSerializers.to[immutable.Seq]
    )
  }

  def getTopicMethodWithName[Message](clazz: Class[_], name: String): ScalaMethodTopic[Message] = {
    new ScalaMethodTopic[Message](
      clazz.getMethods.find(_.getName == name).getOrElse(throw new NoSuchMethodException(name))
    )
  }

  def methodForImpl0[Q, R](c: Context)(f: c.Tree)(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f)

  // Execute in REPL to generate:
  // (1 to 22).foreach { i => println(s"  def methodForImpl$i[Q, R](c: Context)(f: c.Tree)(" + (1 to i).map(p => s"p$p: c.Expr[PPS[_]]").mkString(", ") + s")(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f" + (1 to i).map(p => s", p$p").mkString("") + ")") }
  def methodForImpl1[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1)
  def methodForImpl2[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2)
  def methodForImpl3[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3)
  def methodForImpl4[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4)
  def methodForImpl5[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5)
  def methodForImpl6[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6)
  def methodForImpl7[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7)
  def methodForImpl8[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8)
  def methodForImpl9[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9)
  def methodForImpl10[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10)
  def methodForImpl11[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11)
  def methodForImpl12[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12)
  def methodForImpl13[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13)
  def methodForImpl14[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14)
  def methodForImpl15[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15)
  def methodForImpl16[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16)
  def methodForImpl17[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17)
  def methodForImpl18[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18)
  def methodForImpl19[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19)
  def methodForImpl20[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]], p20: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20)
  def methodForImpl21[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]], p20: c.Expr[PPS[_]], p21: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21)
  def methodForImpl22[Q, R](c: Context)(f: c.Tree)(p1: c.Expr[PPS[_]], p2: c.Expr[PPS[_]], p3: c.Expr[PPS[_]], p4: c.Expr[PPS[_]], p5: c.Expr[PPS[_]], p6: c.Expr[PPS[_]], p7: c.Expr[PPS[_]], p8: c.Expr[PPS[_]], p9: c.Expr[PPS[_]], p10: c.Expr[PPS[_]], p11: c.Expr[PPS[_]], p12: c.Expr[PPS[_]], p13: c.Expr[PPS[_]], p14: c.Expr[PPS[_]], p15: c.Expr[PPS[_]], p16: c.Expr[PPS[_]], p17: c.Expr[PPS[_]], p18: c.Expr[PPS[_]], p19: c.Expr[PPS[_]], p20: c.Expr[PPS[_]], p21: c.Expr[PPS[_]], p22: c.Expr[PPS[_]])(implicit qType: c.WeakTypeTag[Q], rType: c.WeakTypeTag[R]): c.Expr[ScalaMethodServiceCall[Q, R]] = methodForImpl[Q, R](c)(f, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21, p22)

  private def methodForImpl[Request, Response](c: Context)(f: c.Tree, ps: c.Expr[PathParamSerializer[_]]*)(implicit requestType: c.WeakTypeTag[Request], responseType: c.WeakTypeTag[Response]): c.Expr[ScalaMethodServiceCall[Request, Response]] = {
    import c.universe._

    val pathParamSerializers = c.Expr[Seq[PathParamSerializer[_]]](
      Apply(
        Select(reify(Seq).tree, TermName("apply")),
        ps.map(_.tree).toList
      )
    )

    val (thisType, methodName) = f match {
      // Functions references with parameter lists
      case Block((_, Function(_, Apply(Select(This(tt), TermName(tn)), _)))) => (tt, tn)
      // Functions references with no parameter lists
      case Function(_, Select(This(tt), TermName(tn)))                       => (tt, tn)
      // Pass in the result of a function with a parameter list
      case Apply(Select(This(tt), TermName(tn)), _)                          => (tt, tn)
      // Pass in the result of a function with no parameter list
      case Select(This(tt), TermName(tn))                                    => (tt, tn)
      case other =>
        c.abort(c.enclosingPosition, "methodFor must only be invoked with a reference to a function on this, for example, methodFor(this.someFunction _)")
    }

    val methodNameString = Literal(Constant(methodName))

    c.Expr[ScalaMethodServiceCall[Request, Response]](q"_root_.com.lightbend.lagom.scaladsl.api.ServiceSupport.getServiceCallMethodWithName[${requestType.tpe}, ${responseType.tpe}](classOf[$thisType], $methodNameString, $pathParamSerializers)")
  }

  def topicMethodForImpl[Message](c: Context)(f: c.Tree)(implicit messageType: c.WeakTypeTag[Message]): c.Expr[ScalaMethodTopic[Message]] = {
    import c.universe._

    val (thisType, methodName) = f match {
      // Functions references with parameter lists
      case Block((_, Function(_, Apply(Select(This(tt), TermName(tn)), _)))) => (tt, tn)
      // Functions references with no parameter lists
      case Function(_, Select(This(tt), TermName(tn)))                       => (tt, tn)
      // Pass in the result of a function with a parameter list
      case Apply(Select(This(tt), TermName(tn)), _)                          => (tt, tn)
      // Pass in the result of a function with no parameter list
      case Select(This(tt), TermName(tn))                                    => (tt, tn)
      case other =>
        c.abort(c.enclosingPosition, s"${other.getClass}")

        c.abort(c.enclosingPosition, "topicMethodFor must only be invoked with a reference to a function on this, for example, methodFor(this.someFunction)")
    }

    val methodNameString = Literal(Constant(methodName))

    c.Expr[ScalaMethodTopic[Message]](q"_root_.com.lightbend.lagom.scaladsl.api.ServiceSupport.getTopicMethodWithName[${messageType.tpe}](classOf[$thisType], $methodNameString)")
  }

}
