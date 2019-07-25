# Using Lightbend Platform with Lagom

Lagom is part of the [Lightbend Platform](https://www.lightbend.com/lightbend-platform) — an operationally robust solution for deploying Reactive Microservices. It gives you the ability to infuse your applications with intelligence based on real-time streaming and Machine Learning (ML). Lagom services can be developed, deployed and run in production using only open source components. However, we recommend considering the advanced features provided by Lightbend Platform.

This page highlights the Lightbend Platform features that are especially useful for most Lagom users, but there are many more. You can find an overview on the [Lightbend web site](https://www.lightbend.com/lightbend-platform) or [contact Lightbend](https://www.lightbend.com/contact) to learn more.

## Akka Enhancements

Lagom is based on Akka, a set of open-source libraries for designing scalable, resilient systems that span processor cores and networks. [Akka Enhancements](https://doc.akka.io/docs/akka-enhancements/current/) is a suite of useful components that complement Akka and Lagom, available as part of the [Lightbend Platform Subscription](https://www.lightbend.com/lightbend-platform-subscription). These can be included as dependencies of your Lagom services to enable their functionality. It includes:

### Akka Resilience Enhancements

* [Split Brain Resolver](https://doc.akka.io/docs/akka-enhancements/current/split-brain-resolver.html) - Improved resilience for Akka Cluster applications.
* [Kubernetes Lease](https://doc.akka.io/docs/akka-enhancements/current/kubernetes-lease.html) - Kubernetes enhanced failure-recovery for Lagom and Akka Cluster apps.
* [Thread Starvation Detector](https://doc.akka.io/docs/akka-enhancements/current/starvation-detector.html) - Flag performance bottlenecks.
* [Configuration Checker](https://doc.akka.io/docs/akka-enhancements/current/config-checker.html) - Validate configuration against known issues.
* [Diagnostics Recorder](https://doc.akka.io/docs/akka-enhancements/current/diagnostics-recorder.html) - Facilitates enhanced support from Lightbend.

### Akka Persistence Enhancements

* [Multi-DC Persistence](https://doc.akka.io/docs/akka-enhancements/current/persistence-dc/index.html) - Persistence across data centers.
* [GDPR for Akka Persistence](https://doc.akka.io/docs/akka-enhancements/current/gdpr/index.html) - Safe deletion of confidential data.

See the documentation for each of these to understand if they apply to your use of Lagom.

We strongly recommend using the Split Brain Resolver with all services that use the Lagom Persistence API or other cluster-based functionality. Read about the importance of the Split Brain Resolver in the [[Cluster Downing|Cluster#Downing]] documentation.

## Telemetry and Console

Lightbend Platform also includes observability features to ensure the health and availability of your Lagom services. This has two essential pieces: [Lightbend Telemetry](https://developer.lightbend.com/docs/telemetry/current/home.html) (code named "Cinnamon") and [Lightbend Console](https://developer.lightbend.com/docs/console/current/). Telemetry makes it possible to gather metric, event and trace information from Akka, Scala, Play, and Lagom based applications. The information is transferred to various backends such as Prometheus. The Console provides visibility for KPIs, reactive metrics, monitors and alerting, and includes a large selection of ready-to-use dashboards.

To use these with Lagom, the Lightbend Telemetry agent must be included as a dependency of your Lagom services. See the [Lightbend Telemetry documentation](https://developer.lightbend.com/docs/telemetry/current/home.html) for details:

* [Features](https://developer.lightbend.com/docs/telemetry/current/introduction/overview/features.html)
* [Configuring Lagom services to use Telemetry](https://developer.lightbend.com/docs/telemetry/current/instrumentations/lagom/lagom.html)
* [Java example project](https://developer.lightbend.com/docs/telemetry/current/getting-started/lagom_java.html)
* [Scala example project](https://developer.lightbend.com/docs/telemetry/current/getting-started/lagom_scala.html)

See [Integrating Lagom with Lightbend Telemetry](https://github.com/lagom/lagom-samples/blob/1.6.x/lightbend-telemetry/lightbend-telemetry-java-mvn/README.md) for a Java example of integrating Telemetry into a Lagom service.

## Configuring a Lagom build for Lightbend Platform

Bintray credentials are required to build applications using the Lightbend Platform. Lightbend customers should log into the [support portal](https://portal.lightbend.com/ReactivePlatform/EnterpriseSuiteCredentials) to obtain their credentials. Follow the links below to see how to supply the credentials when using sbt or Maven.

* [Lightbend Platform setup for sbt](https://developer.lightbend.com/docs/lightbend-platform/2.0/setup/setup-sbt.html)
* [Lightbend Platform setup for Maven](https://developer.lightbend.com/docs/lightbend-platform/2.0/setup/setup-maven.html)


[Contact Lightbend](https://www.lightbend.com/contact) to get started with Lightbend Platform.
