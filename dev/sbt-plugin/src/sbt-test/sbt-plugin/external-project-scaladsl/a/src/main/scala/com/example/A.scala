package com.example

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import scala.concurrent.Future

trait A extends Service {
  def hello(name: String): ServiceCall[NotUsed, String]

  override def descriptor = {
    import Service._
    named("a").withCalls(
      pathCall("/hello/:name", hello _)
    ).withAutoAcl(true)
  }
}

class AImpl extends A {
  override def hello(name: String) = ServiceCall { _ =>
    Future.successful(s"Hello $name")
  }
}

abstract class AApplication(context: LagomApplicationContext)
  extends LagomApplication(context) with AhcWSComponents {

  override def lagomServer = LagomServer.forServices(bindService[A].to(new AImpl))
}

class ALoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new AApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new AApplication(context) with LagomDevModeComponents
}