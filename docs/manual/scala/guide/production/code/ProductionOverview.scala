package docs.scaladsl.production.overview

package configurationservicelocator {

  import docs.scaladsl.services.lagomappliaction.HelloApplication

  //#configuration-service-locator
  import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
  import com.lightbend.lagom.scaladsl.server._
  import com.lightbend.lagom.scaladsl.client.ConfigurationServiceLocatorComponents

  class HelloApplicationLoader extends LagomApplicationLoader {

    override def load(context: LagomApplicationContext) =
      new HelloApplication(context) with ConfigurationServiceLocatorComponents

    override def loadDevMode(context: LagomApplicationContext) =
      new HelloApplication(context) with LagomDevModeComponents

  }
  //#configuration-service-locator
}
