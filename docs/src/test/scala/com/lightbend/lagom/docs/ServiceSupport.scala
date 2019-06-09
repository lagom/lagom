/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.docs

import java.util.concurrent.CompletionStage

import com.google.inject.AbstractModule
import com.lightbend.lagom.javadsl.api.transport.RequestHeader
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.api.ServiceCall
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.server.HeaderServiceCall
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import play.api.Application
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.core.server.Server

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.compat.java8.FutureConverters._
import com.lightbend.lagom.internal.testkit.TestServiceLocator
import docs.services.test.ServiceTestModule
import com.lightbend.lagom.internal.testkit.TestServiceLocatorPort
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcPersistenceModule
import com.typesafe.config.ConfigFactory
import play.api.db.DBModule
import play.api.db.HikariCPModule

trait ServiceSupport extends WordSpecLike with Matchers {

  def withServer[T](
      applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  )(block: Application => T): T = {
    val port                   = Promise[Int]()
    val testServiceLocatorPort = TestServiceLocatorPort(port.future)

    val application =
      applicationBuilder
        .configure("lagom.cluster.bootstrap.enabled" -> "off", "lagom.cluster.exit-jvm-when-system-terminated" -> "off")
        .bindings(bind[TestServiceLocatorPort].to(testServiceLocatorPort))
        .overrides(bind[ServiceLocator].to(classOf[TestServiceLocator]))
        .disable(classOf[ServiceTestModule]) // enabled in application.conf
        .build()

    Server.withApplication(application) { assignedPort =>
      port.success(assignedPort.value)
      block(application)
    }
  }

  trait WithService[S] {
    def apply[T](block: Application => S => T): T
  }

  def withService[S <: Service: ClassTag, I <: S: ClassTag](
      applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  ): WithService[S] =
    withServiceImpl(applicationBuilder.bindings(new AbstractModule with ServiceGuiceSupport {
      override def configure(): Unit = {
        val serviceClass: Class[S] = implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[S]]
        val implClass: Class[I]    = implicitly[ClassTag[I]].runtimeClass.asInstanceOf[Class[I]]
        bindService(serviceClass, implClass)
      }
    }))

  def withServiceInstance[S <: Service: ClassTag](
      impl: S,
      applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  ): WithService[S] =
    withServiceImpl(
      applicationBuilder
        .bindings(new AbstractModule with ServiceGuiceSupport {
          override def configure(): Unit = {
            bindService(implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[S]], impl)
          }
        })
    )

  private def withServiceImpl[S: ClassTag](
      applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
  ): WithService[S] = new WithService[S] {
    def apply[T](block: Application => S => T) = {
      withServer(
        applicationBuilder
          .disable(classOf[JdbcPersistenceModule], classOf[HikariCPModule], classOf[DBModule])
      ) { app =>
        block(app)(app.injector.instanceOf[S])
      }
    }
  }

  def serviceCall[Request, Response](function: Request => Future[Response]): ServiceCall[Request, Response] = {
    new ServiceCall[Request, Response] {
      override def invoke(request: Request): CompletionStage[Response] = function(request).toJava
    }
  }

  def serviceCall[Request, Response](
      function: (RequestHeader, Request) => Future[(ResponseHeader, Response)]
  ): ServiceCall[Request, Response] = {
    new HeaderServiceCall[Request, Response] {
      override def invokeWithHeaders(
          header: RequestHeader,
          request: Request
      ): CompletionStage[akka.japi.Pair[ResponseHeader, Response]] =
        function(header, request).map(r => akka.japi.Pair(r._1, r._2)).toJava
    }
  }

}
