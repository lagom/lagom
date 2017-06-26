/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it

import java.util.function.{ Function => JFunction }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.scalatest.{ Inside, Matchers, WordSpecLike }
import play.api.Application
import play.inject.guice.GuiceApplicationBuilder
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag
import akka.japi.function.Procedure
import com.lightbend.lagom.javadsl.testkit.ServiceTest
import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer

trait ServiceSupport extends WordSpecLike with Matchers with Inside {

  def withServer(configureBuilder: GuiceApplicationBuilder => GuiceApplicationBuilder)(block: Application => Unit): Unit = {
    val jConfigureBuilder = new JFunction[GuiceApplicationBuilder, GuiceApplicationBuilder] {
      override def apply(b: GuiceApplicationBuilder): GuiceApplicationBuilder = {
        configureBuilder(b)
      }
    }
    val jBlock = new Procedure[TestServer] {
      override def apply(server: TestServer): Unit = {
        block(server.app.getWrappedApplication)
      }
    }
    val setup = ServiceTest.defaultSetup.configureBuilder(jConfigureBuilder).withCluster(false)
    ServiceTest.withServer(setup, jBlock)
  }

  def withClient[T: ClassTag](configureBuilder: GuiceApplicationBuilder => GuiceApplicationBuilder)(block: Application => T => Unit): Unit = {
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
