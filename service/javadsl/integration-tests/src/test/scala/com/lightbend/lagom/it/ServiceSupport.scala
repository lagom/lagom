/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it

import java.util.Collections
import java.util.function.{ Function => JFunction }

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.scalatest.Inside
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import play.api.Application
import play.api.Configuration
import play.api.Environment
import play.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag
import akka.japi.function.Procedure
import com.google.inject.Binder
import com.google.inject.Module
import com.google.inject.TypeLiteral
import com.lightbend.lagom.javadsl.testkit.ServiceTest
import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer
import play.api.routing.Router
import java.util

import com.lightbend.lagom.internal.testkit.EmptyAdditionalRoutersModule

sealed trait HttpBackend {
  final val provider: String = s"play.core.server.${codeName}ServerProvider"
  val codeName: String
}

case object AkkaHttp extends HttpBackend {
  val codeName = "AkkaHttp"
}

case object Netty extends HttpBackend {
  val codeName = "Netty"
}

trait ServiceSupport extends WordSpecLike with Matchers with Inside {
  def withServer(
      configureBuilder: GuiceApplicationBuilder => GuiceApplicationBuilder
  )(block: Application => Unit)(implicit httpBackend: HttpBackend): Unit = {
    val jConfigureBuilder = new JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder] {
      override def apply(b: GuiceApplicationBuilder): GuiceApplicationBuilder = {
        configureBuilder(b)
          .overrides(EmptyAdditionalRoutersModule)
          .configure("play.server.provider", httpBackend.provider)
      }
    }
    val jBlock = new Procedure[TestServer] {
      override def apply(server: TestServer): Unit = {
        block(server.app.asScala())
      }
    }
    val setup = ServiceTest.defaultSetup.configureBuilder(jConfigureBuilder).withCluster(false)
    ServiceTest.withServer(setup, jBlock)
  }

  def withClient[T: ClassTag](
      configureBuilder: GuiceApplicationBuilder => GuiceApplicationBuilder
  )(block: Application => T => Unit)(implicit httpBackend: HttpBackend): Unit = {
    withServer(configureBuilder) { application =>
      val client = application.injector.instanceOf[T]
      block(application)(client)
    }
  }

  implicit def materializer(implicit app: Application): Materializer = app.materializer

  def consume[T](source: Source[T, _])(implicit mat: Materializer): List[T] = {
    Await.result(source.runFold(List.empty[T])((list, t) => t :: list), 10.seconds).reverse
  }
}
