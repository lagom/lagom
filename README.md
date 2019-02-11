# Akka Discovery based ServiceLocator

This project provides:

* `AkkaDiscoveryServiceLocator`, a Lagom `ServiceLocator` implementation based on [Akka Discovery][]
* `AkkaDiscoveryServiceLocatorModule`, a Guice `Module` to wire-up the `AkkaDiscoveryServiceLocator` when using runtime-time DI.
* `AkkaDiscoveryComponents`, a cake component to wire-up the `AkkaDiscoveryServiceLocator` when using compile-time DI (macwire).

This is an experimental project. The API and configuration may change.

[Akka Discovery]: https://doc.akka.io/docs/akka/2.5/discovery/index.html
