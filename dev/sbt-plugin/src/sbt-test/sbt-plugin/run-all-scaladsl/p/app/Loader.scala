import play.api.{ Application, ApplicationLoader, BuiltInComponentsFromContext }
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire._
import router.Routes
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.client.LagomServiceClientComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import scala.collection.immutable

class Loader extends ApplicationLoader {
  def load(context: ApplicationLoader.Context): Application = {
    new BuiltInComponentsFromContext(context)
      with LagomServiceClientComponents
      with AhcWSComponents
      with LagomDevModeComponents
      with controllers.AssetsComponents
    {
      override lazy val serviceInfo = ServiceInfo("p", Map("p" -> immutable.Seq(
        ServiceAcl.forPathRegex("/p"),
        ServiceAcl.forPathRegex("/assets/.*")
      )))
      override lazy val router = {
        val prefix = "/"
        wire[Routes]
      }
      override lazy val httpFilters = Nil
      lazy val applicationController = wire[controllers.Application]
    }.application
  }
}