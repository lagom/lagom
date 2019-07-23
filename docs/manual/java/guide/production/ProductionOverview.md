# Running Lagom in production

Lagom can be set up to work with most production environments, ranging from basic, manual deployments on physical servers to fully-managed service orchestration platforms such as [OpenShift](https://www.openshift.com/), [Kubernetes](https://kubernetes.io/) and [Mesosphere DC/OS](https://dcos.io/). The core APIs in Lagom are independent of any particular deployment platform. Where Lagom does need to interface with the environment, it provides extensible plugin points that allow you to configure your services for production without requiring changes to your service implementation code.

The following information provides an overview of production considerations that apply to any environment. If you are deploying your service in OpenShift clusters, you can also refer to the [Deploying Lightbend applications to OpenShift](https://developer.lightbend.com/guides/openshift-deployment/) guide.

The production considerations can be summed up as setting up and tuning some or all of the following:

* production of artifacts (ZIP archives, docker images, ...)
* service location (other lagom services, external services, databases, etc...)
* Akka Cluster formation
* expose lifecycle data (readiness, liveness)

## Production considerations

The production environment determines the methods for packaging your services, managing Akka Cluster formation, and providing service location, including that for Cassandra and Kafka:

* When using [[sbt builds|LagomBuild#Defining-a-build-in-sbt]], Lagom leverages the open-source [`sbt-native-packager`](https://www.scala-sbt.org/sbt-native-packager/) plugin to produce packages:

    * By default, sbt produces standalone "universal" [zip archives](https://www.scala-sbt.org/sbt-native-packager/formats/universal.html) containing the compiled service, all of its dependencies, and a start script. These have no special infrastructure requirements and can be unzipped and run just about anywhere that supports Java. However, this includes no built-in facility for process monitoring and recovering from crashes, so for a resilient production environment, you'll need to provide this another way.

    * You can also use `sbt-native-packager` to build and publish [Docker images](https://developer.lightbend.com/docs/lightbend-orchestration/current/building.html) that can be used with Kubernetes, DC/OS, or any other Docker-compatible production environment.

    * Otherwise, you can configure `sbt-native-packager` to produce [other archive formats](https://www.scala-sbt.org/sbt-native-packager/formats/universal.html#build), [Windows (MSI) installers](https://www.scala-sbt.org/sbt-native-packager/formats/windows.html), Linux packages in [Debian](https://www.scala-sbt.org/sbt-native-packager/formats/debian.html) or [RPM](https://www.scala-sbt.org/sbt-native-packager/formats/rpm.html) format and more.

* Maven has a variety of plugins to produce artifacts for various platforms:

    * The [Maven Assembly Plugin](http://maven.apache.org/plugins/maven-assembly-plugin/) can be used to build archives in zip, tar and other similar formats.

    * The [Docker Maven Plugin](https://dmp.fabric8.io/) is recommended for building Docker images.

    * Other Maven packaging plugins in varying states of maturity are available from the [MojoHaus Project](https://www.mojohaus.org/plugins.html).

* At runtime, services need to locate the addresses of other services to communicate with them. This requires you to configure an implementation of a [`ServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html) that Lagom uses to look up the addresses of services by their names. The production environment you choose might provide a service discovery mechanism that you can use with Lagom.

    * For simple deployments, Lagom includes a built-in service locator that uses hardcoded addresses specified in the service configuration ([described below](#Using-static-values-for-services-and-Cassandra)).

    * When using dynamic deployments where processes don't run on static IPs, you can use  [[Akka Discovery Service Locator|AkkaDiscoveryIntegration]]   that can integrate with the service discovery features of Kubernetes or DC/OS, or any other environment supported by [Akka's Service Discovery](https://doc.akka.io/docs/akka/2.6/discovery/index.html).

    * Otherwise, you can implement the interface yourself to integrate with a service registry of your choosing (such as [Consul](https://www.consul.io/), [ZooKeeper](https://zookeeper.apache.org/), or [etcd](https://coreos.com/etcd/)) or start with an open-source example implementation such as [`lagom-service-locator-consul`](https://github.com/jboner/lagom-service-locator-consul) or [`lagom-service-locator-zookeeper`](https://github.com/jboner/lagom-service-locator-zookeeper).

* Lagom services that require an [Akka Cluster](https://doc.akka.io/docs/akka/current/cluster-usage.html) (which includes any that use the Lagom [[Persistence|PersistentEntity]] or [[Publish-Subscribe|PubSub]] APIs) must have a strategy for forming a cluster or joining an existing cluster on startup.

    * If you don't use a service orchestration platform and can determine the addresses of some of your nodes in advance of deploying them, Akka Cluster can be configured manually by listing the addresses of [seed nodes](https://doc.akka.io/docs/akka/current/cluster-usage.html#joining-to-seed-nodes) in the service configuration.

    * When you don't specify a static list of `seed-nodes`, Lagom will enable the [Akka Cluster Bootstrap](https://doc.akka.io/docs/akka-management/current/bootstrap/index.html) extension for integration with other service discovery infrastructure. Using Akka Cluster Bootstrap requires setting up a `Service Discovery` method (for example [Service Discovery for Kubernetes](https://doc.akka.io/docs/akka-management/current/discovery/kubernetes.html)). See the [[Lagom Cluster|Cluster]] documentation for more information.

* The Akka Cluster Bootstrap feature introduced above requires enabling [Akka management HTTP](https://doc.akka.io/docs/akka-management/current/). That is an Akka HTTP server shared by multiple features, running on [port 8558](https://github.com/akka/akka-management/blob/v1.0.0-RC3/management/src/main/resources/reference.conf#L17) by default (that port is configurable). Lagom will start the Akka management HTTP automatically anytime a feature that requires it is enabled. See also the [[Lagom Cluster|Cluster#Akka-Management]] documentation.

    * Akka management HTTP also powers Akka-provided Health Checks. [Health Checks](https://doc.akka.io/docs/akka-management/current/healthchecks.html) is a pluggable mechanism where library providers and users can register logic to determine if a service is alive and/or ready. Lagom will enable Akka Cluster Membership Health Checks by default any time the Lagom service requires a cluster. Akka Health Checks register, by default, the `ready/` and `alive/` routes and implement the [readiness and aliveness](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#container-probes) semantics as described by Kubernetes.

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

The [`ConfigurationServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/client/ConfigurationServiceLocator.html) reads the service locator configuration from Lagom's `application.conf` file.  Here is an example that specifies static locations for two Lagom services:

```
lagom.services {
  serviceA = "http://10.1.2.3:8080"
  serviceB = "http://10.1.2.4:8080"
}
```

To instruct Lagom to use the `ConfigurationServiceLocator`, you need to bind it to the [`ServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html) class in your Guice module. Of course, you don't have to configure a separate module, this configuration can be added to your existing Guice module. Since Lagom already provides a service locator in dev mode, you will likely only want to bind this when Lagom is in production mode.  Play supports passing its `Environment` and `Configuration` objects to module constructors, so you'll need to add those to your module:

@[content](code/docs/production/ConfigurationServiceLocatorModule.java)


To hardcode the Cassandra contact points when using a static service locator, add the following to the `application.conf` file:

```
lagom.services {
  cas_native = "tcp://10.1.2.3:9042"
}
```
