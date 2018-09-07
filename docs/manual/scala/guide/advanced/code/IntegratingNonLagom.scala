package docs.scaladsl.advanced

package staticservicelocator {

  import docs.scaladsl.services.helloservice.HelloService

  class MyAppStandalone {
    //#static-service-locator-standalone
    import java.net.URI
    import com.lightbend.lagom.scaladsl.client._
    import play.api.libs.ws.ahc.AhcWSComponents

    val clientFactory = new StandaloneLagomClientFactory("my-client", classOf[StandaloneLagomClientFactory].getClassLoader)
      with StaticServiceLocatorComponents
      with AhcWSComponents {

      override def staticServiceUri = URI.create("http://localhost:8080")
    }
    //#static-service-locator-standalone

    //#stop-application
    clientFactory.stop()
    //#stop-application

    //#create-client
    val helloService = clientApplication.serviceClient.implement[HelloService]
    //#create-client
  }


  class MyApp {
    //#static-service-locator
    import java.net.URI
    import com.lightbend.lagom.scaladsl.client._
    import play.api.libs.ws.ahc.AhcWSComponents

    class MyLagomClientFactory(val actorSystem: ActorSystem, val materialzer: Materializer)
      extends LagomClientFactory("my-client", classOf[MyLagomClientFactory].getClassLoader)
      with StaticServiceLocatorComponents
      with AhcWSComponents {
        override def staticServiceUri = URI.create("http://localhost:8080")
    }

    val actorSystem = ActorSystem("my-app")
    val materializer = ActorMaterializer()(actorSystem)
    val clientFactory = new MyLagomClientFactory(actorSystem, materializer)
    //#static-service-locator

    clientApplication.stop()

    val helloService = clientApplication.serviceClient.implement[HelloService]
  }
}



package devmode {

  class MyApp {
    val devMode = true

    //#dev-mode
    import java.net.URI
    import com.lightbend.lagom.scaladsl.client._
    import play.api.libs.ws.ahc.AhcWSComponents
    import com.lightbend.lagom.scaladsl.devmode.LagomDevModeServiceLocatorComponents


    val clientApplication = if (devMode) {
      new LagomClientApplication("my-client")
        with AhcWSComponents
        with LagomDevModeServiceLocatorComponents
    } else {
      new LagomClientApplication("my-client")
        with StaticServiceLocatorComponents
        with AhcWSComponents {

        override def staticServiceUri = URI.create("http://localhost:8080")
      }
    }
    //#dev-mode

    //#dev-mode-url
    new LagomClientApplication("my-client")
      with AhcWSComponents
      with LagomDevModeServiceLocatorComponents {

      override lazy val devModeServiceLocatorUrl = URI.create("http://localhost:8001")
    }

    //#dev-mode-url
  }

}
