# Using Lightbend Enterprise Suite with Lagom

Lagom is part of the [Lightbend Reactive Platform](https://www.lightbend.com/products/lightbend-reactive-platform) — an application development solution for building Reactive systems on the JVM powered by an open source core and the commercial [Enterprise Suite](https://www.lightbend.com/products/enterprise-suite). Lagom services can be developed, deployed and run in production using only open source components, however we recommend considering the advanced features provided by Enterprise Suite.

This page highlights the Enterprise Suite features that are especially useful for most Lagom users, but there are many more. You can find an overview on the [Lightbend web site](https://www.lightbend.com/products/enterprise-suite) or [contact Lightbend](https://www.lightbend.com/contact) to learn more.

## Akka Commercial Addons

Lagom is based on Akka, a set of open-source libraries for designing scalable, resilient systems that span processor cores and networks. [Akka Commercial Addons](https://developer.lightbend.com/docs/akka-commercial-addons/current/index.html) is a suite of useful components that complement Akka and Lagom. These can be included as dependencies of your Lagom services to enable their functionality. It includes:

* [Split Brain Resolver](https://developer.lightbend.com/docs/akka-commercial-addons/current/split-brain-resolver.html)
* [Multi-DC Persistence](https://developer.lightbend.com/docs/akka-commercial-addons/current/persistence-dc/index.html)
* [Fast Failover](https://developer.lightbend.com/docs/akka-commercial-addons/current/fast-failover.html)
* [Configuration Checker](https://developer.lightbend.com/docs/akka-commercial-addons/current/config-checker.html)
* [Diagnostics Recorder](https://developer.lightbend.com/docs/akka-commercial-addons/current/diagnostics-recorder.html)
* [Thread Starvation Detector](https://developer.lightbend.com/docs/akka-commercial-addons/current/starvation-detector.html)
* [GDPR for Akka Persistence](https://developer.lightbend.com/docs/akka-commercial-addons/current/gdpr/index.html)

See the documentation for each of these to understand if they apply to your use of Lagom.

We strongly recommend using the Split Brain Resolver with all services that use the Lagom Persistence API or other cluster-based functionality. Read about the importance of the Split Brain Resolver in the [[Cluster Downing|Cluster#Downing]] documentation.

## Telemetry and Monitoring

Enterprise Suite also includes Intelligent Monitoring features to ensure the health and availability of your Lagom services. This has two essential pieces: [Telemetry (Cinnamon)](https://developer.lightbend.com/docs/cinnamon/current/home.html) and [OpsClarity](https://developer.lightbend.com/docs/opsclarity/current/home.html). Telemetry makes it possible to gather metric, event and trace information from Akka, Scala, Play, and Lagom based applications. The information is transferred to various backends such as OpsClarity or Prometheus. OpsClarity provides an advanced monitoring user interface for visualizing and troubleshooting distributed systems.

To use these with Lagom, the Cinnamon Telemetry agent must be included as a dependency of your Lagom services. See the [Lightbend Telemetry documentation](https://developer.lightbend.com/docs/cinnamon/current/home.html) for details:

* [Features](https://developer.lightbend.com/docs/cinnamon/current/introduction/overview/features.html)
* [Configuring Lagom services to use Telemetry](https://developer.lightbend.com/docs/cinnamon/current/instrumentations/lagom/lagom.html)
* [Java example project](https://developer.lightbend.com/docs/cinnamon/current/getting-started/lagom_java.html)
* [Scala example project](https://developer.lightbend.com/docs/cinnamon/current/getting-started/lagom_scala.html)

See [Integrating Lagom with Lightbend Telemetry](https://github.com/lagom/lagom-recipes/blob/master/lightbend-telemetry/lightbend-telemetry-java-mvn/README.md) for a Java example of integrating Telemetry into a Lagom service.

## Configuring a Lagom build for Enterprise Suite

Bintray credentials are required to build applications using the Enterprise Suite portion of the Reactive Platform. Lightbend customers should log into the [support portal](https://portal.lightbend.com/ReactivePlatform/EnterpriseSuiteCredentials) to obtain their credentials. Follow the links below to see how to supply the credentials when using sbt or Maven.

* [Reactive Platform setup for sbt](https://developer.lightbend.com/docs/reactive-platform/2.0/setup/setup-sbt.html)
* [Reactive Platform setup for Maven](https://developer.lightbend.com/docs/reactive-platform/2.0/setup/setup-maven.html)


[Contact Lightbend](https://www.lightbend.com/contact) to get started with Enterprise Suite.
