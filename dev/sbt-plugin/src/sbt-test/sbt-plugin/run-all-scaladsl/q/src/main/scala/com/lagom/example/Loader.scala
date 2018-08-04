import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire._
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.client.LagomServiceClientComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.routing.Router

import scala.collection.immutable

class App(context: Context)
  extends BuiltInComponentsFromContext(context)
    with play.filters.HttpFiltersComponents {

  import play.api.routing.sird._

  val health: Router.Routes = {
    case GET(p"/q") =>
      Action {
        Ok(Response.response)
      }
  }

  override val router: Router = Router.from(health)

}

final class Loader extends ApplicationLoader {
  def load(context: Context): Application = {
    // Configure the Logger.
    // More info:
    //  - https://www.playframework.com/documentation/2.6.x/ScalaCompileTimeDependencyInjection#Configuring-Logging
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }

    new App(context).application
  }
}
