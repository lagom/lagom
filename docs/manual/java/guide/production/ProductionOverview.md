# Production

Lagom doesn't prescribe any particular production environment, however out of the box support is provided for [Lightbend ConductR](https://www.lightbend.com/products/conductr).

## Deploying Lagom services to ConductR

Lightbend ConductR is a perfect match for Lagom, as it provides the following features:

* a means to manage configuration distinctly from your packaged artifact;
* consolidated logging across many nodes;
* a supervisory system whereby if your service(s) terminate unexpectedly then they are automatically restarted;
* the ability to scale up and down with ease and with speed;
* handling of network failures, in particular those that can lead to a split brain scenario;
* automated seed node discovery when requiring more than one instance of your service so that they may share a cluster;
* the ability to perform rolling updates of your services;
* support for your services being monitored across a cluster; and
* the ability to test your services locally prior to them being deployed.

To deploy your Lagom services using ConductR, see [[ConductR]].

## Deploying to other platforms

Lagom sbt support leverages the [sbt-native-packager](http://www.scala-sbt.org/sbt-native-packager/) to produce archives of various types. By default zip archives can be produced, but you can also produce tar.gz, MSI, debian, RPM, Docker and more.

If using Maven, there are many plugins for Maven to produce artifacts for various platforms.

Running a package requires the provision of a service locator implementation i.e. something that provides the ability for your service to be able to lookup the location of another dynamically at runtime. At a technical level, you provide an implementation of a [ServiceLocator](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html).

### Deploying using static service locations

While we would never advise using static service locations in any production situation, as a means to demonstrating a working Lagom system in the absence of a managed runtime, you may decide to deploy Lagom systems to static locations with a static configuration saying where the systems live.

To aid in achieving this, a [`ConfigurationServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/api/ConfigurationServiceLocator.html) is provided that reads the service locator configuration out of Lagom's `application.conf` file.  Here is an example of the configuration for it:

```
lagom.services {
  serviceA = "http://10.1.2.3:8080"
  serviceB = "http://10.1.2.4:8080"
}
```

To instruct Lagom to use the `ConfigurationServiceLocator`, you need to bind it to the [`ServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html) class in your Guice module.  Since Lagom already provides a service locator in dev mode, you will likely only want to bind this when Lagom is in production mode.  Play supports passing its `Environment` and `Configuration` objects to module constructors, so you'll need to add those to your module:

@[content](code/docs/production/ConfigurationServiceLocatorModule.java)

Of course, you don't have to configure a separate module, this configuration can be added to your existing Guice module.

### Deploying using static Cassandra contact-points

Static lookup is the default behavior in `akka-persistence-cassandra` but Lagom overrides that behavior implementing a Session provider based on service location. That allows all services to continue to operate without the need to redeploy if/when the Cassandra `contact-points` are updated or fail. Using a Service Location based approach provides higher resiliency. It is possible to hardcode the list of `contact-points` where Cassandra may be located even when the server is stared with a dynamic service locator.

You can decide to hardcode the Cassandra contact points when using a static service locator as described above using:

```
lagom.services {
  cas_native = "tcp://10.1.2.3:9042"
}
```

But that is only possible if all your service uses a static service locator.

If you want to use dynamic service location for your services but need to statically locate Cassandra you may use the following setup in the `application.conf` of your service:

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

This setup disables Lagom's `ConfigSessionProvider` and falls back to that provided in `akka-persistence-cassandra` which uses the list of endpoints listed in `contact-points`.

This configuration is part of `application.conf` and therefore it will be applied in all environments (development and production) unless overridden. See developer mode settings on [[overriding Cassandra setup in Dev Mode|CassandraServer#Connecting-to-a-locally-running-Cassandra-instance]] for more information on settings up Cassandra in dev mode.
