# Akka Discovery based ServiceLocator

This project provides a Lagom `ServiceLocator` implementation based on Akka Discovery. 

# Usage 

It provides:

* A `AkkaDiscoveryServiceLocator` implementation based on [Akka Discovery](https://developer.lightbend.com/docs/akka-management/current/discovery/index.html)
* A `AkkaDiscoveryServiceLocatorModule` Guice module to wire-up the `AkkaDiscoveryServiceLocator` when using runtime-time DI.
* A `AkkaDiscoveryComponents` cake component to wire-up the `AkkaDiscoveryServiceLocator` when using compile-time DI (macwire).

This is an experimental project. API and configuration may change.
