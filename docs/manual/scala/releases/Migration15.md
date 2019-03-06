# Lagom 1.5 Migration Guide

This guide explains how to migrate from Lagom 1.4 to Lagom 1.5. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.5 updates to the latest major versions of Play (2.7), Akka (2.5.21) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.7. Please refer to the Play 2.7 migration guide (https://www.playframework.com/documentation/2.7.0/Migration27), [Akka 2.5.21 release announcement](https://akka.io/blog/news/2019/02/13/akka-2.5.21-released) and the [Akka HTTP 10.1.x release announcements](https://akka.io/blog/news-archive.html) for more details.

For a detailed list of  version upgrades for Slick, Alpakka-Kafka, Kafka, etc... refer to the [release notes](https://github.com/lagom/lagom/releases).

## Build changes

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.5.0-RC1")
```

We also recommend upgrading to sbt 1.2.1 or later, by updating the `sbt.version` in `project/build.properties`.

## Deprecations

### Service Ports

Lagom 1.5.0 now has support for SSL calls for gRPC integration and a new build setting was introduced to configure the https port for a given service manually.

In sbt, the new setting is called `lagomServiceHttpsPort`. To keep the names aligned, we are deprecating `lagomServicePort` in favour of `lagomServiceHttpPort`.

### Persistence testkit `TestUtil`

The following:

* `com.lightbend.lagom.scaladsl.persistence.cassandra.testkit.TestUtil` (`lagom-scaladsl-persistence-cassandra`)
* `com.lightbend.lagom.scaladsl.persistence.jdbc.testkit.TestUtil` (`lagom-scaladsl-persistence-jdbc`)
* `com.lightbend.lagom.scaladsl.persistence.testkit.AbstractTestUtil` (`lagom-scaladsl-persistence`)

were never intended for public consumption, and therefore have been marked deprecated in 1.5.0 for removal in
2.0.0.

## TLS Support

Lagom in both [[dev mode|ConfiguringServicesInDevelopment#Using-HTTPS-in-development-mode]] and [[tests|TestingServices#How-to-use-TLS-on-tests]] supports basic usage of TLS by means of self-signed certificates provided by the framework.

## ConductR

ConductR is no longer supported with Lagom 1.5.

We recommend migrating to Kubernetes, DC/OS or another deployment solution before upgrading.

[Lighbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) is an open-source suite of tools that helps you deploy Lagom services to Kubernetes and DC/OS. It provides an easy way to create Docker images for your applications and introduces an automated process for generating Kubernetes and DC/OS resource and configuration files for you from those images. This process helps reduce the friction between development and operations. If you are using Kubernetes or DC/OS, or interested in trying one of these platforms, we encourage you to read the Lightbend Orchestration documentation to understand how to use it with Lagom and other components of the [Lightbend Reactive Platform](https://www.lightbend.com/products/reactive-platform).

If you are not using Kubernetes or DC/OS, you must configure your services in a way that suits your production environment. See [[Running Lagom in production|ProductionOverview]] for more information.

If you are using ConductR in production and need assistance migrating to other solutions please [contact Lightbend](https://www.lightbend.com/contact).

## Lightbend Orchestration

TODO (new version required)

## Cluster Formation

A new mechanism to form and [[join an Akka Cluster|Cluster#Joining]] is introduced in Lagom 1.5. Apart from the original `Manual Cluster Formation` the new `Akka Cluster Bootstrap` is now supported. This new mechanism is introduced with lower precedence than `Manual Cluster Formation` so if you rely on the use of a list of `seed-nodes` then everything will work as before. On the other hand, `Akka Cluster Bootstrap` takes precedence over the `join-self` cluster formation for single-node clusters. If you use single-node clusters via `join-self` you will have to explicitly disable `Akka Cluster Bootstrap`:

```
lagom.cluster.bootstrap.enabled = false
```

## Upgrading a production system

TODO

### Rolling upgrade

TODO

### Downtime upgrade

TODO
