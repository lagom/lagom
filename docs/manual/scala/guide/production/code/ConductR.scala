package com.typesafe.conductr.bundlelib.lagom.scaladsl {

  import com.lightbend.lagom.scaladsl.api.ServiceLocator
  import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator

  // We stub this here because we can't have a binary dependency on conductr-bundlelib as that would introduce a
  // circular dependency between conductr-bundlelib and lagom.
  trait ConductRApplicationComponents {
    lazy val serviceLocator: ServiceLocator = NoServiceLocator
  }
}

package docs.scaladsl.production.conductr {

  package conductrapplication {

    import docs.scaladsl.services.lagomapplication.HelloApplication
    import docs.scaladsl.services.helloservice.HelloService

    //#conductr-application
    import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
    import com.lightbend.lagom.scaladsl.server._
    import com.typesafe.conductr.bundlelib.lagom.scaladsl.ConductRApplicationComponents

    class HelloApplicationLoader extends LagomApplicationLoader {

      override def load(context: LagomApplicationContext) =
        new HelloApplication(context) with ConductRApplicationComponents

      override def loadDevMode(context: LagomApplicationContext) =
        new HelloApplication(context) with LagomDevModeComponents

      override def describeServices = List(
        readDescriptor[HelloService]
      )
    }
    //#conductr-application
  }

}