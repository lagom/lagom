# Using Akka Discovery

As of version 1.5.1, Lagom has built-in integration with [Akka Discovery](https://doc.akka.io/docs/akka/2.6/discovery/index.html) throught a  [ServiceLocator](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html) that wraps Akka Discovery. This `ServiceLocator` implementation is called `AkkaDiscoveryServiceLocator`. This is the recommended implementation for production specially for users targeting Kubernetes and DC/OS (Marathon).

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

By default, Lagom uses [Aggregate multiple discovery methods](https://doc.akka.io/docs/akka/2.6/discovery/index.html#discovery-method-aggregate-multiple-discovery-methods). The first discovery method is set to Configuration and the second is set to DNS. So the static definition of service endpoints has a priority over DNS discovery.

To statically configure service endpoints in your `application.conf` file consult the [Aggregate multiple discovery methods](https://doc.akka.io/docs/akka/2.6/discovery/index.html#discovery-method-aggregate-multiple-discovery-methods) documentation.

## DNS SRV vs. DNS A/AAAA Lookups

`AkkaDiscoveryServiceLocator` supports DNS SRV as well as DNS A lookups. It defaults to SRV lookups since it's the most common usage in environments like Kubernetes and DC/OS (Marathon).

Since Lagom's `ServiceLocator` API does not support `port-name` and `protocol` fields as used in SRV lookups, `AkkaDiscoveryServiceLocator` will use default values as fallback. The default `port-name` is `http` (as defined by setting `lagom.akka.discovery.defaults.port-name`) and the default `protocol` is `tcp`  (as defined by setting `lagom.akka.discovery.defaults.port-protocol`).

Those values are only used if a lookup is done for a string that does not comply with the [SRV format](https://en.wikipedia.org/wiki/SRV_record). For instance, when looking for another Lagom service using a Lagom service client. In such a case, the lookup is done using the service name, as defined by its `ServiceDescriptor`, and the defaults for `port-name` and `protocol`.

If both `lagom.akka.discovery.defaults.port-name` and `lagom.akka.discovery.defaults.port-protocol` are set to `null` or a blank string, the lookups are done without those values which correspond to a simple DNS A lookup.

## Confinguring Service Mappings

It's possible to override those values on a per service base using `service-name-mappings`.

You may map a service name to a SRV string as in:

@[service-name-to-srv](../../../common/guide/production/code/akka-discovery-config-examples.conf)

You can also override the port name and protocol to force a DNS A lookup:

@[service-name-dns-A-loopup](../../../common/guide/production/code/akka-discovery-config-examples.conf)

This per service override will allow a DNS A lookup the cassandra server while any other lookups will still use the defaults.

The default settings are in defined in the reference configuration as:

@[lagom-akka-discovery-reference-conf](../../../../../akka-service-locator/core/src/main/resources/reference.conf)

Note: this component was [previous published](https://github.com/lagom/lagom-akka-discovery-service-locator) as an independent library. If you have it on your classpath it's recommended to remove it and use the one being provided by Lagom directly.
