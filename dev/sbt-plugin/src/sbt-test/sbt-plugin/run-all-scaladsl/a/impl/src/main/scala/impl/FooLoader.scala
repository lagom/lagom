package impl

import java.nio.file.{Files, StandardOpenOption}
import java.util.Date

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import api.FooService
import com.softwaremill.macwire._

class FooLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new FooApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new FooApplication(context) with LagomDevModeComponents
}

abstract class FooApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  override lazy val lagomServer = LagomServer.forServices(
    bindService[FooService].to(wire[FooServiceImpl])
  )

  Files.write(environment.getFile("target/reload.log").toPath, s"${new Date()} - reloaded\n".getBytes("utf-8"),
    StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}
