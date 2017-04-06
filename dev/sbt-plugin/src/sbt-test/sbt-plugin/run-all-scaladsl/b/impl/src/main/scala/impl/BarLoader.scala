package impl

import java.nio.file.{Files, StandardOpenOption}
import java.util.Date

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import api.{BarService, FooService}
import com.softwaremill.macwire._

class BarLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new BarApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new BarApplication(context) with LagomDevModeComponents
}

abstract class BarApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  override lazy val lagomServer = serverFor[BarService](wire[BarServiceImpl])


  lazy val fooService = serviceClient.implement[FooService]

  Files.write(environment.getFile("target/reload.log").toPath, s"${new Date()} - reloaded\n".getBytes("utf-8"),
    StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}
