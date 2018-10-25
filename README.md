# Akka Discovery based ServiceLocator

This project provides a few components to facilitate the deployment of a Lagom application. 

It provides:

* A `AkkaDiscoveryServiceLocator` implementation based on [Akka Discovery](https://developer.lightbend.com/docs/akka-management/current/discovery/index.html)
* A `AkkaDiscoveryServiceLocatorModule` Guice module to wire-up the `AkkaDiscoveryServiceLocator` and bootstrap the Akka Cluster.
* A `akka-management` `ManagementRouteProvider` extension implementation that can be used for health checks bound to Akka Cluster formation.
* A [sample application](https://github.com/lagom/service-locator-akka-discovery/tree/master/sample/hello-kubernetes-java-mvn) to demonstrate its usage.

This is an experimental project. API and configuration may change.
