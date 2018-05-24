# Running Lagom in production

Lagom can be set up to work with most production environments, ranging from basic, manual deployments on physical servers to fully-managed service orchestration platforms such as [Kubernetes](https://kubernetes.io/) and [Mesosphere DC/OS](https://dcos.io/). The core APIs in Lagom are independent of any particular deployment platform. Where Lagom does need to interface with the environment, it provides extensible plugin points that allow you to configure your services for production without requiring changes to your service implementation code.

[Lightbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) is an open-source suite of tools that helps you deploy Lagom services to Kubernetes and DC/OS. It provides an easy way to create Docker images for your applications and introduces an automated process for generating Kubernetes and DC/OS resource and configuration files for you from those images. This process helps reduce the friction between development and operations. If you are using Kubernetes or DC/OS, or interested in trying one of these platforms, we encourage you to read the Lightbend Orchestration documentation to understand how to use it with Lagom and other components of the [Lightbend Reactive Platform](https://www.lightbend.com/products/reactive-platform).

If you are not using Kubernetes or DC/OS, you must configure your services in a way that suits your production environment. The following information provides an overview of production considerations that apply to any environment.

## Production considerations

The production environment determines the methods for packaging your services, managing Akka Cluster formation, and providing service location, including that for Cassandra and Kafka:

* Lagom leverages the open-source [`sbt-native-packager`](https://www.scala-sbt.org/sbt-native-packager/) plugin to produce packages:

    * By default, sbt produces standalone "universal" [zip archives](https://www.scala-sbt.org/sbt-native-packager/formats/universal.html) containing the compiled service, all of its dependencies, and a start script. These have no special infrastructure requirements and can be unzipped and run just about anywhere that supports Java. However, this includes no built-in facility for process monitoring and recovering from crashes, so for a resilient production environment, you'll need to provide this another way.

    * Lightbend Orchestration automatically configures `sbt-native-packager` to build and publish [Docker images](https://developer.lightbend.com/docs/lightbend-orchestration/current/building.html) that can be used with Kubernetes, DC/OS, or any other Docker-compatible production environment.

    * Otherwise, you can configure `sbt-native-packager` to produce [other archive formats](https://www.scala-sbt.org/sbt-native-packager/formats/universal.html#build), [Windows (MSI) installers](https://www.scala-sbt.org/sbt-native-packager/formats/windows.html), Linux packages in [Debian](https://www.scala-sbt.org/sbt-native-packager/formats/debian.html) or [RPM](https://www.scala-sbt.org/sbt-native-packager/formats/rpm.html) format and more.

* At runtime, services need to locate the addresses of other services to communicate with them. This requires you to configure an implementation of a [`ServiceLocator`](api/com/lightbend/lagom/scaladsl/api/ServiceLocator.html) that Lagom uses to look up the addresses of services by their names. The production environment you choose might provide a service discovery mechanism that you can use with Lagom.

    * For simple deployments, Lagom includes a built-in service locator that uses addresses specified in the service configuration ([described below](#Using-static-values-for-services-and-Cassandra)).

    * Lightbend Orchestration provides an open-source [`ServiceLocator` implementation](https://developer.lightbend.com/docs/lightbend-orchestration/current/features.html#service-location) that integrates with the service discovery features of Kubernetes or DC/OS, or any other environment that supports service discovery via DNS.

    * Otherwise, you can implement the interface yourself to integrate with a service registry of your choosing (such as [Consul](https://www.consul.io/), [ZooKeeper](https://zookeeper.apache.org/), or [etcd](https://coreos.com/etcd/)) or start with an open-source example implementation such as [`lagom-service-locator-consul`](https://github.com/jboner/lagom-service-locator-consul) or [`lagom-service-locator-zookeeper`](https://github.com/jboner/lagom-service-locator-zookeeper).

* Services that require an [Akka Cluster](https://doc.akka.io/docs/akka/current/cluster-usage.html) (which includes any that use the Lagom [[Persistence|PersistentEntity]] or [[Publish-Subscribe|PubSub]] APIs) must have a strategy for forming a cluster or joining an existing cluster on startup.

    * If you don't use a service orchestration platform and can determine the addresses of some of your nodes in advance of deploying them, Akka Cluster can be configured manually by listing the addresses of [seed nodes](https://doc.akka.io/docs/akka/current/cluster-usage.html#joining-to-seed-nodes) in the service configuration.

    * Lightbend Orchestration includes open-source support for [automatic Akka Cluster formation](https://developer.lightbend.com/docs/lightbend-orchestration/current/features.html#service-location) on Kubernetes or DC/OS.

    * Otherwise, you can use the open-source [Akka Cluster Bootstrap](https://developer.lightbend.com/docs/akka-management/current/bootstrap.html) extension for integration with other service discovery infrastructure, or write your own programmatic cluster formation implementation. See the [[Lagom Cluster|Cluster]] documentation for more information.

* Lagom's Cassandra module comes out of the box ready to locate your Cassandra cluster using the service locator. This means Lagom considers Cassandra like any other external service it may need to locate. If your production environment requires it, you can also choose to bypass the service locator by providing Cassandra contact points directly in the service configuration, as described in the [section below](#Using-static-Cassandra-contact-points).

* Similarly, Lagom’s Kafka integration uses the service locator by default to look up bootstrap servers for the Kafka client. This can also be overridden to specify a list of brokers in the service configuration. See the [[Lagom Kafka Client|KafkaClient]] documentation for more information.

## Using static Cassandra contact points

If you want to use dynamic service location for your services but need to statically locate Cassandra, modify the `application.conf` for your service. You will need to disable Lagom's `ConfigSessionProvider` and fall back to the one provided in `akka-persistence-cassandra`, which uses the list of endpoints listed in `contact-points`. The `application.conf` settings will be applied in all environments (development and production) unless overridden. See developer mode settings on [[overriding Cassandra setup in Dev Mode|CassandraServer#Connecting-to-a-locally-running-Cassandra-instance]] for more information on settings up Cassandra in dev mode.

To set up static Cassandra `contact-points` and disable `ConfigSessionProvider`, modify the following sections of the `application.conf` file:

```
cassandra.default {
  ## list the contact points  here
  contact-points = ["10.0.1.71", "23.51.143.11"]
  ## override Lagom’s ServiceLocator-based ConfigSessionProvider
  session-provider = akka.persistence.cassandra.ConfigSessionProvider
}

cassandra-journal {
  contact-points = ${cassandra.default.contact-points}
  session-provider = ${cassandra.default.session-provider}
}

cassandra-snapshot-store {
  contact-points = ${cassandra.default.contact-points}
  session-provider = ${cassandra.default.session-provider}
}

lagom.persistence.read-side.cassandra {
  contact-points = ${cassandra.default.contact-points}
  session-provider = ${cassandra.default.session-provider}
}
```

## Using static values for services and Cassandra

You can deploy Lagom systems to static locations by using static configuration. When using static service location, you can also hardcode Cassandra locations. To achieve this, follow these steps:

1. Specify service locations in `application.conf`.
2. Bind the `ConfigurationServiceLocator` in your Guice module.
3. Optionally add Cassandra locations in `application.conf`.


The [`ConfigurationServiceLocator`](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocator.html) reads the service locator configuration from Lagom's `application.conf` file.  Here is an example that specifies static locations for two Lagom services:

```
lagom.services {
  serviceA = "http://10.1.2.3:8080"
  serviceB = "http://10.1.2.4:8080"
}
```

To instruct Lagom to use the `ConfigurationServiceLocator`, you need to mix the [`ConfigurationServiceLocatorComponents`](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocatorComponents.html) trait into your application in production mode, as shown in the following example. Use the `LagomDevModeComponents` trait in a development environment.

@[configuration-service-locator](code/ProductionOverview.scala)

To hardcode the Cassandra contact points when using a static service locator, add the following to the `application.conf` file:

```
lagom.services {
  cas_native = "tcp://10.1.2.3:9042"
}
```



