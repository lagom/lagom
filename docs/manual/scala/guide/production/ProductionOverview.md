# Production

Lagom doesn't prescribe any particular production environment, however out of the box support is provided for [Lightbend ConductR](https://www.lightbend.com/products/conductr). If you are interested in deploying on [Kubernetes](https://kubernetes.io/), see our guide that demonstrates [how to deploy the Chirper example application](https://developer.lightbend.com/guides/k8s-microservices/).

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

## Considerations for deploying to other platforms

The deployment platform determines the type of archive you will need to use for packaging your microservices and the way you provide service location, including that for Cassandra: 

* Lagom leverages the [sbt-native-packager](http://www.scala-sbt.org/sbt-native-packager/) to produce archives of various types. By default, sbt produces zip archives, but you can easily produce tar.gz, MSI, debian, RPM, Docker and more.

* At runtime, services need to locate each other. This requires you to provide an implementation of a  [ServiceLocator](api/com/lightbend/lagom/scaladsl/api/ServiceLocator.html). And, the deployment platform you choose might impose its own requirements on configuration. 

* The Cassandra module provided by `akka-persistence-cassandra` uses static lookup by default. Lagom overrides that behavior by implementing a Session provider based on service location. That allows all services to continue to operate without the need to redeploy if/when the Cassandra `contact-points` are updated or fail. Using this approach provides higher resiliency. However, it is possible to hardcode the list of `contact-points` where Cassandra may be located even when the server is stared with a dynamic service locator as described in the section below.

### Deploying using static Cassandra contact-points

If you want to use dynamic service location for your services but need to statically locate Cassandra, modify the `application.conf` for your service. You will need to disable Lagom's `ConfigSessionProvider` and fall back to the one provided in `akka-persistence-cassandra`, which uses the list of endpoints listed in `contact-points`. The `application.conf` settings will be applied in all environments (development and production) unless overridden. See developer mode settings on [[overriding Cassandra setup in Dev Mode|CassandraServer#Connecting-to-a-locally-running-Cassandra-instance]] for more information on settings up Cassandra in dev mode. 

To set up static Cassandra `contact-points` and disable `ConfigSessionProvider`, modify the following sections of the `application-conf` file:

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

### Using static values for services and Cassandra to simulate a managed runtime

While we would never advise using static service locations in production, to simulate a working Lagom system in the absence of a managed runtime, you can deploy Lagom systems to static locations by using static configuration. When using static service location, you can also hardcode Cassandra locations. To achieve this, follow these steps:

1. Specify service locations in `application.conf`.
    The [`ConfigurationServiceLocator`](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocator.html reads service locator configuration out of Lagom's `application.conf` file.  This example specifies static locations for two Lagom services:

    ```
    lagom.services {
      serviceA = "http://10.1.2.3:8080"
      serviceB = "http://10.1.2.4:8080"
    }
```

1. In production mode, add the [`ConfigurationServiceLocatorComponents`](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocatorComponents.html) trait in your application, as shown in the following example. Use the `LagomDevModeComponents` trait in a development environment.

    @[configuration-service-locator](code/ProductionOverview.scala)

1. Optionally, to hard code Cassandra locations, add them in `application.conf`as values for `cas_native`, as illustrated below:

    ```
    lagom.services {
      cas_native = "tcp://10.1.2.3:9042"
    }
    ```



