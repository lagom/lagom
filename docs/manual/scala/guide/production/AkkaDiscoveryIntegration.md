# Using Akka Discovery

As of version 1.5.1, Lagom has built-in integration with [Akka Discovery](https://doc.akka.io/docs/akka/2.5/discovery/index.html) throught a   [ServiceLocator](api/com/lightbend/lagom/scaladsl/api/ServiceLocator.html) that wraps Akka Discovery. This is the recommended implementation for production specially for users targeting Kubernetes and DC/OS (Marathon).

## Dependency

To use this feature add the following in your project's build:

@[akka-discovery-dependency](code/akka-discovery-dependency.sbt)

## Configuration

Once you have it in your project you can add the component to your `LagomApplicationLoader`.

@[akka-discovery-service-locator](code/AkkaDiscoveryIntegration.scala)

Next, you will need to choose one of the available Akka Discovery implementations and configure it in your `application.conf` file. Consult the [Akka Discovery](https://doc.akka.io/docs/akka/2.5/discovery/index.html) documentation for further instructions.

Note: this component was [previous published](https://github.com/lagom/lagom-akka-discovery-service-locator) as an independent library. If you have it on your classpath it's recommended to remove it and use the one being provided by Lagom directly.
