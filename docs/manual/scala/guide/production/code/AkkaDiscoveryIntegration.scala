/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.production.overview

package akkadiscoveryservicelocator {

  import docs.scaladsl.services.lagomapplication.HelloApplication

  //#akka-discovery-service-locator
  import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
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
