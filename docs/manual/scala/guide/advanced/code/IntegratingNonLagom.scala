package docs.scaladsl.advanced

package staticservicelocator {

  import docs.scaladsl.services.helloservice.HelloService

  class MyApp {
    //#static-service-locator
    import java.net.URI
    import com.lightbend.lagom.scaladsl.client._
    import play.api.libs.ws.ahc.AhcWSComponents

    val clientApplication = new LagomClientApplication("my-client")
      with StaticServiceLocatorComponents
      with AhcWSComponents {

      override def staticServiceUri = URI.create("http://localhost:8080")
    }
    //#static-service-locator

    //#stop-application
    clientApplication.stop()
    //#stop-application

    //#create-client
    val helloService = clientApplication.serviceClient.implement[HelloService]
    //#create-client
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