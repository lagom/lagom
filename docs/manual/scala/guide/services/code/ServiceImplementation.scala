/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.services

package helloservice {
  //#hello-service-impl
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import scala.concurrent.Future

  class HelloServiceImpl extends HelloService {
    override def sayHello = ServiceCall { name =>
      Future.successful(s"Hello $name!")
    }
  }
  //#hello-service-impl
}

package lagomapplication {

  import helloservice._

  //#lagom-application
  import com.lightbend.lagom.scaladsl.server._
  import play.api.libs.ws.ahc.AhcWSComponents
  import com.softwaremill.macwire._

  abstract class HelloApplication(context: LagomApplicationContext)
      extends LagomApplication(context)
      with AhcWSComponents {

    override lazy val lagomServer = serverFor[HelloService](wire[HelloServiceImpl])
  }
  //#lagom-application
}

package lagomloader {

  import lagomapplication._
  import helloservice._

  //#lagom-loader
  import com.lightbend.lagom.scaladsl.server._
  import com.lightbend.lagom.scaladsl.api.ServiceLocator
  import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents

  class HelloApplicationLoader extends LagomApplicationLoader {

    override def loadDevMode(context: LagomApplicationContext) =
      new HelloApplication(context) with LagomDevModeComponents

    override def load(context: LagomApplicationContext) =
      new HelloApplication(context) {
        override def serviceLocator = ServiceLocator.NoServiceLocator
      }

    override def describeService = Some(readDescriptor[HelloService])
  }
  //#lagom-loader
}

package callstream {

  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api.ServiceCall

  import scala.concurrent.Future

  class CallStreamImpl extends CallStream {
    //#tick-service-call
    import scala.concurrent.duration._
    import akka.stream.scaladsl.Source

    override def tick(intervalMs: Int) = ServiceCall { tickMessage =>
      Future.successful(
        Source
          .tick(
            intervalMs.milliseconds,
            intervalMs.milliseconds,
            tickMessage
          )
          .mapMaterializedValue(_ => NotUsed)
      )
    }
    //#tick-service-call
  }
}

package hellostream {

  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import scala.concurrent.Future

  class HelloStreamImpl extends HelloStream {
    //#hello-service-call
    override def sayHello = ServiceCall { names =>
      Future.successful(names.map(name => s"Hello $name!"))
    }
    //#hello-service-call
  }
}

package serverservicecall {

  import scala.concurrent.Future

  class UsingServerServiceCall extends helloservice.HelloService {
    //#server-service-call
    import com.lightbend.lagom.scaladsl.server.ServerServiceCall
    import com.lightbend.lagom.scaladsl.api.transport.ResponseHeader

    override def sayHello = ServerServiceCall { (requestHeader, name) =>
      val user = requestHeader.principal
        .map(_.getName)
        .getOrElse("No one")
      val response = s"$user wants to say hello to $name"

      val responseHeader = ResponseHeader.Ok
        .withHeader("Server", "Hello service")

      Future.successful((responseHeader, response))
    }
    //#server-service-call
  }
}

package servicecallcomposition {

  import scala.concurrent.ExecutionContext
  import scala.concurrent.Future
  import com.lightbend.lagom.scaladsl.server.ServerServiceCall

  object Logging {
    //#logging-service-call
    def logged[Request, Response](serviceCall: ServerServiceCall[Request, Response]) =
      ServerServiceCall.compose { requestHeader =>
        println(s"Received ${requestHeader.method} ${requestHeader.uri}")
        serviceCall
      }
    //#logging-service-call
  }

  class LoggedHelloService extends helloservice.HelloService {
    import Logging.logged

    //#logged-hello-service
    override def sayHello =
      logged(ServerServiceCall { name =>
        Future.successful(s"Hello $name!")
      })
    //#logged-hello-service
  }

  trait User

  //#user-storage
  trait UserStorage {
    def lookupUser(username: String): Future[Option[User]]
  }
  //#user-storage

  object Authentication {
    val userStorage: UserStorage      = null
    implicit val ec: ExecutionContext = null

    //#auth-service-call
    import com.lightbend.lagom.scaladsl.api.transport.Forbidden

    def authenticated[Request, Response](
        serviceCall: User => ServerServiceCall[Request, Response]
    ) = ServerServiceCall.composeAsync { requestHeader =>
      // First lookup user
      val userLookup = requestHeader.principal
        .map(principal => userStorage.lookupUser(principal.getName))
        .getOrElse(Future.successful(None))

      // Then, if it exists, apply it to the service call
      userLookup.map {
        case Some(user) => serviceCall(user)
        case None       => throw Forbidden("User must be authenticated to access this service call")
      }
    }
    //#auth-service-call
  }

  class AuthHelloService extends helloservice.HelloService {
    import Authentication.authenticated
    //#auth-hello-service
    override def sayHello = authenticated { user =>
      ServerServiceCall { name =>
        Future.successful(s"$user is saying hello to $name")
      }
    }
    //#auth-hello-service
  }

  object Filter {
    import Logging.logged
    import Authentication.authenticated

    //#compose-service-call
    def filter[Request, Response](
        serviceCall: User => ServerServiceCall[Request, Response]
    ) = logged(authenticated(serviceCall))
    //#compose-service-call
  }

  class FilterHelloService extends helloservice.HelloService {
    import Filter.filter
    //#filter-hello-service
    override def sayHello = filter { user =>
      ServerServiceCall { name =>
        Future.successful(s"$user is saying hello to $name")
      }
    }
    //#filter-hello-service
  }

}
