/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.services

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceAcl
import com.lightbend.lagom.scaladsl.api.ServiceCall
import play.api.routing.SimpleRouter
import scala.concurrent.Future

package additionalrouters {

  package helloservice {

    //#hello-service
    trait HelloService extends Service {

      def hello(id: String): ServiceCall[NotUsed, String]

      final override def descriptor = {
        import Service._
        named("hello")
          .withCalls(
            pathCall("/api/hello/:id", hello _).withAutoAcl(true)
          )
          .withAcls(
            // extra ACL to expose additional router endpoint on ServiceGateway
            ServiceAcl(pathRegex = Some("/api/files"))
          )
      }
    }
    //#hello-service

    //#hello-service-impl
    class HelloServiceImpl extends HelloService {
      override def hello(name: String) = ServiceCall { _ =>
        Future.successful(s"Hello $name!")
      }
    }
    //#hello-service-impl
  }

  package lagomapplication {

    import helloservice._

    import com.lightbend.lagom.scaladsl.server._
    import play.api.libs.ws.ahc.AhcWSComponents
    import com.softwaremill.macwire._

    package sompeplayrouter {

      import play.api.routing.Router.Routes

      class SomePlayRouter extends SimpleRouter {
        override def routes: Routes = ???
      }

      abstract class HelloApplication(context: LagomApplicationContext)
          extends LagomApplication(context)
          with AhcWSComponents {

        //#lagom-application-some-play-router
        override lazy val lagomServer =
          serverFor[HelloService](wire[HelloServiceImpl])
            .additionalRouter(wire[SomePlayRouter])
        //#lagom-application-some-play-router
      }
    }

    package fileuploadrouter {
      //#file-upload-router
      import play.api.mvc.DefaultActionBuilder
      import play.api.mvc.PlayBodyParsers
      import play.api.mvc.Results
      import play.api.routing.Router
      import play.api.routing.sird._

      class FileUploadRouter(action: DefaultActionBuilder, parser: PlayBodyParsers) {
        val router = Router.from {
          case POST(p"/api/files") =>
            action(parser.multipartFormData) { request =>
              val filePaths = request.body.files.map(_.ref.getAbsolutePath)
              Results.Ok(filePaths.mkString("Uploaded[", ", ", "]"))
            }
        }
      }
      //#file-upload-router

      abstract class HelloApplication(context: LagomApplicationContext)
          extends LagomApplication(context)
          with AhcWSComponents {

        //#lagom-application-file-upload
        override lazy val lagomServer =
          serverFor[HelloService](wire[HelloServiceImpl])
            .additionalRouter(wire[FileUploadRouter].router)
        //#lagom-application-file-upload
      }
    }

  }
}
