# Production

Lagom doesn't prescribe any particular production environment. Out-of-the-box support is provided for [Lightbend ConductR](https://www.lightbend.com/products/conductr). Lightbend ConductR is a perfect match for Lagom, as it provides the following features:

* a means to manage configuration distinctly from your packaged artifact;
* consolidated logging across many nodes;
* a supervisory system whereby if your service(s) terminate unexpectedly then they are automatically restarted;
* the ability to scale up and down with ease and with speed;
* handling of network failures, in particular those that can lead to a split brain scenario;
* automated seed node discovery when requiring more than one instance of your service so that they may share a cluster;
* the ability to perform rolling updates of your services;
* support for your services being monitored across a cluster; and
* the ability to test your services locally prior to them being deployed.

To deploy your Lagom services using ConductR, see [[ConductR]]. If you are interested in deploying on [Kubernetes](https://kubernetes.io/), see our guide that demonstrates [how to deploy the Chirper example application](https://developer.lightbend.com/guides/k8s-microservices/).

## Considerations for deploying to other platforms

The deployment platform determines the type of archive you will need to use for packaging your microservices as well as the way you set up service location. For packaging: 

* Lagom sbt support leverages the [sbt-native-packager](http://www.scala-sbt.org/sbt-native-packager/) to produce archives of various types. By default zip archives can be produced, but you can also produce tar.gz, MSI, debian, RPM, Docker and more.

* Maven has a variety of plugins to produce artifacts for various platforms.

At runtime, services need to locate each other. This requires you to provide an implementation of a [ServiceLocator](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html). And, the deployment platform you choose might impose its own requirements on configuration. 

The Cassandra module provided by `akka-persistence-cassandra` uses static lookup by default. Lagom overrides that behavior by implementing a Session provider based on service location. That allows all services to continue to operate without the need to redeploy if/when the Cassandra `contact-points` are updated or fail. Using this approach provides higher resiliency. However, it is possible to hardcode the list of `contact-points` where Cassandra may be located even when the server is stared with a dynamic service locator as described in the section below.

### Using static Cassandra contact points

If you want to use dynamic service location for your services but need to statically locate Cassandra,  you can set up `contact-points` in the `application.conf` file for your service. This disables Lagom's `ConfigSessionProvider` and falls back to that provided in `akka-persistence-cassandra` which uses the list of endpoints listed in `contact-points`. The `application.conf` configuration applies in all environments (development and production) unless overridden. See developer mode settings on [[overriding Cassandra setup in Dev Mode|CassandraServer#Connecting-to-a-locally-running-Cassandra-instance]] for more information on settings up Cassandra in dev mode.

The following example shows how to set `contact-points` in the `application.conf` file:

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

## Using static values for services and Cassandra to simulate a managed runtime 

While we would never advise using static service locations in production, to simulate a working Lagom system in the absence of a managed runtime, you can deploy Lagom systems to static locations by using static configuration. When using static service location, you can also hardcode Cassandra locations. To achieve this, you will need to:

1. Specify service locations in `application.conf`.
2. Bind the `ConfigurationServiceLocator` in your Guice module.
3. Optionally add Cassandra locations in `application.conf`.

The  [`ConfigurationServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/client/ConfigurationServiceLocator.html) reads the service locator configuration out of Lagom's `application.conf` file.  Here is an example that specifies static locations for two Lagom services:

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


