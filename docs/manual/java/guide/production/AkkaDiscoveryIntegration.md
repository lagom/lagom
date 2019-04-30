# Using Akka Discovery

As of version 1.5.1, Lagom has built-in integration with [Akka Discovery](https://doc.akka.io/docs/akka/2.5/discovery/index.html) throught a  [ServiceLocator](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html) that wraps Akka Discovery. This is the recommended implementation for production specially for users targeting Kubernetes and DC/OS (Marathon).

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

Next, you will need to choose one of the available Akka Discovery implementations and configure it in your `application.conf` file. Consult the [Akka Discovery](https://doc.akka.io/docs/akka/2.5/discovery/index.html) documentation for further instructions.

Note: this component was [previous published](https://github.com/lagom/lagom-akka-discovery-service-locator) as an independent library. If you have it on your classpath it's recommended to remove it and use the one being provided by Lagom directly.
