/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.production.overview

package akkadiscoveryservicelocator {
  import docs.scaladsl.services.lagomapplication.HelloApplication

  //#akka-discovery-service-locator
  import com.lightbend.lagom.devmode.scaladsl.LagomDevModeComponents
  import com.lightbend.lagom.scaladsl.server._
  import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents

  class HelloApplicationLoader extends LagomApplicationLoader {
    override def load(context: LagomApplicationContext) =
      new HelloApplication(context) with AkkaDiscoveryComponents

    override def loadDevMode(context: LagomApplicationContext) =
      new HelloApplication(context) with LagomDevModeComponents
  }
  //#akka-discovery-service-locator
}
