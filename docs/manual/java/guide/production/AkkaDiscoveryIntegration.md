# Using Akka Discovery

As of version 1.5.1, Lagom has built-in integration with [Akka Discovery](https://doc.akka.io/docs/akka/2.6/discovery/index.html) throught a  [ServiceLocator](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html) that wraps Akka Discovery. This is the recommended implementation for production specially for users targeting Kubernetes and DC/OS (Marathon).

## Dependency

To use this feature add the following in your project's build:

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-akka-discovery-service-locator_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[akka-discovery-dependency](code/akka-discovery-dependency.sbt)

## Configuration

The Guice module [AkkaDiscoveryServiceLocatorModule](api/index.html?com/lightbend/lagom/javadsl/akka/discovery/AkkaDiscoveryServiceLocatorModule.html) will be added by default to your project, but will only wire in the [AkkaDiscoveryServiceLocator](api/index.html?com/lightbend/lagom/javadsl/akka/discovery/AkkaDiscoveryServiceLocator.html) when running in production mode.

In development, your Lagom application will keep using the Lagom's dev-mode [ServiceLocator](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html).

By default, Lagom uses [Aggregate multiple discovery methods](https://doc.akka.io/docs/akka/2.6/discovery/index.html#discovery-method-aggregate-multiple-discovery-methods). The first discovery method is set to Configuration and the second is set to DNS.
So the static definition of service endpoins has a priority over DNS discovery.

To statically configure service endpoints in your `application.conf` file consult the [Aggregate multiple discovery methods](https://doc.akka.io/docs/akka/2.6/discovery/index.html#discovery-method-aggregate-multiple-discovery-methods) documentation.

To configure service discovery with DNS Lagom provides some configuration settings. The default settings are in

@[lagom-akka-discovery-reference-conf](../../../../../akka-service-locator/core/src/main/resources/reference.conf)

Note: this component was [previous published](https://github.com/lagom/lagom-akka-discovery-service-locator) as an independent library. If you have it on your classpath it's recommended to remove it and use the one being provided by Lagom directly.
