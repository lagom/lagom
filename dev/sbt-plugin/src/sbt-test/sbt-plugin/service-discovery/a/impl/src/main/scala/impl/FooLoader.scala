package impl


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

abstract class FooApplication(context: LagomApplicationContext) extends LagomApplication(context) with AhcWSComponents {
  override lazy val lagomServer = serverFor[FooService](wire[FooServiceImpl])
}
