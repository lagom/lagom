package docs.scaladsl.services

package helloservice {

  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import scala.concurrent.Future


  //#hello-service
  trait HelloService extends Service {

    def hello(id: String): ServiceCall[NotUsed, String]

    override final def descriptor = {
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
    override def sayHhello(name: String) = ServiceCall { _ =>
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


  class SomePlayRouter extends SimpleRouter{
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

  //#file-upload-router
  import play.api.mvc.{DefaultActionBuilder, Results}
  import play.api.routing.Router
  import play.api.routing.sird._

  class FileUploadRouter(action: DefaultActionBuilder) {
    val router = Router.from {
      case POST(p"/api/files") => action { _ =>
        // for the sake of simplicity, this implementation
        // only returns a short message for each incoming request.
        Results.Ok("File(s) uploaded")
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
