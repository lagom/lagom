# Using Akka Discovery

As of version 1.5.1, Lagom has built-in integration with [Akka Discovery](https://doc.akka.io/docs/akka/2.6/discovery/index.html) throught a   [ServiceLocator](api/com/lightbend/lagom/scaladsl/api/ServiceLocator.html) that wraps Akka Discovery. This is the recommended implementation for production specially for users targeting Kubernetes and DC/OS (Marathon).

## Dependency

To use this feature add the following in your project's build:

@[akka-discovery-dependency](code/akka-discovery-dependency.sbt)

## Configuration

Once you have it in your project you can add the component to your `LagomApplicationLoader`.

@[akka-discovery-service-locator](code/AkkaDiscoveryIntegration.scala)

By default, Lagom uses [Aggregate multiple discovery methods](https://doc.akka.io/docs/akka/2.6/discovery/index.html#discovery-method-aggregate-multiple-discovery-methods). The first discovery method is set to Configuration and the second is set to DNS.
So the static definition of service endpoins has a priority over DNS discovery.

To statically configure service endpoints in your `application.conf` file consult the [Aggregate multiple discovery methods](https://doc.akka.io/docs/akka/2.6/discovery/index.html#discovery-method-aggregate-multiple-discovery-methods) documentation.

To configure service discovery with DNS Lagom provides some configuration settings. The default settings are in

@[lagom-akka-discovery-reference-conf](../../../../../akka-service-locator/core/src/main/resources/reference.conf)

Note: this component was [previous published](https://github.com/lagom/lagom-akka-discovery-service-locator) as an independent library. If you have it on your classpath it's recommended to remove it and use the one being provided by Lagom directly.
