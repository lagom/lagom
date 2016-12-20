package docs.scaladsl.production.overview

package configurationservicelocator {

  import docs.scaladsl.services.lagomappliaction.HelloApplication

  //#configuration-service-locator
  import com.lightbend.lagom.scaladsl.server._
  import com.lightbend.lagom.scaladsl.api.ConfigurationServiceLocator
  import com.softwaremill.macwire._

  class HelloApplicationLoader extends LagomApplicationLoader {

    override def load(context: LagomApplicationContext) =
      new HelloApplication(context) {
        override lazy val serviceLocator = wire[ConfigurationServiceLocator]
      }

    override def loadDevMode(context: LagomApplicationContext) =
      new HelloApplication(context) with LagomDevModeComponents

  }
  //#configuration-service-locator
}
