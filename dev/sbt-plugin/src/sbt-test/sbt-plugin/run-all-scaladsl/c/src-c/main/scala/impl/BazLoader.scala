package impl

import java.nio.file.{Files, StandardOpenOption}
import java.util.Date

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import play.api.libs.ws.ahc.AhcWSComponents
import api.BazService
import com.softwaremill.macwire._

class BazLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new BazApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new BazApplication(context) with LagomDevModeComponents
}

abstract class BazApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  override lazy val lagomServer = LagomServer.forServices(
    bindService[BazService].to(wire[BazServiceImpl])
  )

  Files.write(environment.getFile("target/reload.log").toPath, s"${new Date()} - reloaded\n".getBytes("utf-8"),
    StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}

