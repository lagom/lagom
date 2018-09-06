# Lagom 1.5 Migration Guide

This guide explains how to migrate from Lagom 1.4 to Lagom 1.5. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.5 also updates to the latest major versions of Play (2.7) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.7. Please refer to the Play 2.7 migration guide (TODO) and the [Akka HTTP 10.1.0 release announcement](https://akka.io/blog/news/2018/03/08/akka-http-10.1.0-released.html) for more details.


## Build changes

### Maven

If you're using a `lagom.version` property in the `properties` section of your root `pom.xml`, then simply update that to `1.5.0-M3`. Otherwise, you'll need to go through every place that a Lagom dependency, including plugins, is used, and set the version there.

### sbt

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.5.0-M3")
```

We also recommend upgrading to sbt 1.2.1 or later, by updating the `sbt.version` in `project/build.properties`.

## Deprecations

### Service Ports

Lagom 1.5.0 now has support for SSL calls for gRPC integration and new a build setting was introduced to configure https port for a given service manually.

In Maven, the new setting is called `serviceHttpsPost`.  To keep the names aligned, we are deprecating  `servicePort` in favour of `serviceHttpPort`.

In sbt, the new setting is called `lagomServiceHttpsPort`. To keep the names aligned, we are deprecating  `lagomServicePort` in favour of `lagomServiceHttpPort`.

TODO: add links to gRPC integration and SSL support documentation.

### Persistence testkit `TestUtil`

The following:

* `com.lightbend.lagom.javadsl.persistence.cassandra.testkit.TestUtil` (`lagom-javadsl-persistence-cassandra`)
* `com.lightbend.lagom.javadsl.persistence.jdbc.testkit.TestUtil` (`lagom-javadsl-persistence-jdbc`)
* `com.lightbend.lagom.javadsl.persistence.testkit.AbstractTestUtil` (`lagom-javadsl-persistence`)

were all never intended for public consumption, and therfore have been marked deprecated in 1.5.0 for removal in
2.0.0.


## ConductR

ConductR is no longer supported with Lagom 1.5.

We recommend migrating to Kubernetes, DC/OS or another deployment solution before upgrading.

[Lighbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) is an open-source suite of tools that helps you deploy Lagom services to Kubernetes and DC/OS. It provides an easy way to create Docker images for your applications and introduces an automated process for generating Kubernetes and DC/OS resource and configuration files for you from those images. This process helps reduce the friction between development and operations. If you are using Kubernetes or DC/OS, or interested in trying one of these platforms, we encourage you to read the Lightbend Orchestration documentation to understand how to use it with Lagom and other components of the [Lightbend Reactive Platform](https://www.lightbend.com/products/reactive-platform).

If you are not using Kubernetes or DC/OS, you must configure your services in a way that suits your production environment. See [[Running Lagom in production|ProductionOverview]] for more information.

If you are using ConductR in production and need assistance migrating to other solutions please [contact Lightbend](https://www.lightbend.com/contact).


## Lightbend Orchestration

TODO (new version required)


## Upgrading a production system

TODO

### Rolling upgrade

TODO

### Downtime upgrade

TODO
