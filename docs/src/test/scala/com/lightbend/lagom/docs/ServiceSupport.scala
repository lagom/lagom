package com.lightbend.lagom.docs

import java.util.concurrent.CompletionStage
import com.google.inject.AbstractModule
import com.lightbend.lagom.javadsl.api.transport.{ ResponseHeader, RequestHeader }
import com.lightbend.lagom.javadsl.api.{ ServiceCall, ServiceLocator }
import com.lightbend.lagom.javadsl.server.{ HeaderServiceCall, ServiceGuiceSupport }
import org.scalatest.{ WordSpecLike, Matchers }
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.core.server.Server
import scala.concurrent.{ Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.compat.java8.FutureConverters._
import com.lightbend.lagom.internal.testkit.TestServiceLocator
import docs.services.test.ServiceTestModule
import com.lightbend.lagom.internal.testkit.TestServiceLocatorPort

trait ServiceSupport extends WordSpecLike with Matchers {

  def withServer[T](applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder())(block: Application => T): T = {
    val port = Promise[Int]()
    val testServiceLocatorPort = TestServiceLocatorPort(port.future)

    val application = applicationBuilder
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

  def withService[S: ClassTag, I <: S: ClassTag](applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()): WithService[S] = new WithService[S] {
    def apply[T](block: Application => S => T) = {
      withServer(applicationBuilder.bindings(new AbstractModule with ServiceGuiceSupport {
        override def configure(): Unit = {
          bindServices(serviceBinding(implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[Any]], implicitly[ClassTag[I]].runtimeClass))
        }
      })) { app =>
        block(app)(app.injector.instanceOf[S])
      }
    }
  }

  def withServiceInstance[S: ClassTag](impl: S, applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()): WithService[S] = new WithService[S] {
    def apply[T](block: Application => S => T) = {
      withServer(applicationBuilder.bindings(new AbstractModule with ServiceGuiceSupport {
        override def configure(): Unit = {
          bindServices(serviceBinding(implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[Any]], impl))
        }
      })) { app =>
        block(app)(app.injector.instanceOf[S])
      }
    }
  }

  def serviceCall[Id, Request, Response](function: (Id, Request) => Future[Response]): ServiceCall[Id, Request, Response] = {
    new ServiceCall[Id, Request, Response] {
      override def invoke(id: Id, request: Request): CompletionStage[Response] = function(id, request).toJava
    }
  }

  def serviceCall[Id, Request, Response](function: (RequestHeader, Id, Request) => Future[(ResponseHeader, Response)]): ServiceCall[Id, Request, Response] = {
    new HeaderServiceCall[Id, Request, Response] {
      override def invokeWithHeaders(header: RequestHeader, id: Id, request: Request): CompletionStage[akka.japi.Pair[ResponseHeader, Response]] =
        function(header, id, request).map(r => akka.japi.Pair(r._1, r._2)).toJava
    }
  }

}
