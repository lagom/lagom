package docs.scaladsl.services

package buildhelloservice {
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import scala.concurrent.Future

  class HelloServiceImpl extends HelloService {
    override def sayHello = ServiceCall { name =>
      Future.successful(s"Hello $name!")
    }
  }
}

package buildlagomapplication {

  import buildhelloservice._

  //#lagom-application
  import com.lightbend.lagom.scaladsl.server._
  import play.api.libs.ws.ahc.AhcWSComponents
  import com.softwaremill.macwire._

  abstract class HelloApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
      with AhcWSComponents {

    override lazy val lagomServer = LagomServer.forServices(
      bindService[HelloService].to(wire[HelloServiceImpl])
    )
  }
  //#lagom-application
}

package buildlagomloader {

  import buildlagomapplication._
  import buildhelloservice._

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

    override def describeServices = List(
      readDescriptor[HelloService]
    )
  }
  //#lagom-loader
}

