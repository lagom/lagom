# Lagom 1.5 Migration Guide

This guide explains how to migrate from Lagom 1.4 to Lagom 1.5. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.5 also updates to the latest major versions of Play (2.7), Akka (2.5.18) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.7. Please refer to the Play 2.7 migration guide (https://www.playframework.com/documentation/2.7.0-RC8/Migration27), [Akka 2.5.18 release announcement](https://akka.io/blog/news/2018/10/07/akka-2.5.18-released) and the [Akka HTTP 10.1.0 release announcement](https://akka.io/blog/news/2018/03/08/akka-http-10.1.0-released.html) for more details.


## Build changes

### Maven

If you're using a `lagom.version` property in the `properties` section of your root `pom.xml`, then simply update that to `1.5.0-RC1`. Otherwise, you'll need to go through every place that a Lagom dependency, including plugins, is used, and set the version there.

### sbt

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.5.0-RC1")
```

We also recommend upgrading to sbt 1.2.1 or later, by updating the `sbt.version` in `project/build.properties`.

## Deprecations

### Service Ports

Lagom 1.5.0 now has support for SSL calls for gRPC integration and a new build setting was introduced to configure the https port for a given service manually.

In Maven, the new setting is called `serviceHttpsPort`.  To keep the names aligned, we are deprecating `servicePort` in favour of `serviceHttpPort`.

In sbt, the new setting is called `lagomServiceHttpsPort`. To keep the names aligned, we are deprecating `lagomServicePort` in favour of `lagomServiceHttpPort`.

### Persistence testkit `TestUtil`

The following:

* `com.lightbend.lagom.javadsl.persistence.cassandra.testkit.TestUtil` (`lagom-javadsl-persistence-cassandra`)
* `com.lightbend.lagom.javadsl.persistence.jdbc.testkit.TestUtil` (`lagom-javadsl-persistence-jdbc`)
* `com.lightbend.lagom.javadsl.persistence.testkit.AbstractTestUtil` (`lagom-javadsl-persistence`)

were never intended for public consumption, and therefore have been marked deprecated in 1.5.0 for removal in
2.0.0.

## TLS Support

Lagom in both [[dev mode|ConfiguringServicesInDevelopment#Using-HTTPS-in-development-mode]] and [[tests|Test#How-to-use-TLS-on-tests]] supports basic usage of TLS by means of self-signed certificates provided by the framework.

## ConductR

ConductR is no longer supported with Lagom 1.5.

We recommend migrating to Kubernetes, DC/OS or another deployment solution before upgrading. If you are using ConductR in production and need assistance migrating to other solutions please [contact Lightbend](https://www.lightbend.com/contact).

See Deployment below.

## Lightbend Orchestration

[Lightbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) is not supported with Lagom 1.5. If you used Lightbend Orchestration for your deployment you will have to migrate to a manual process. This migration may happen on the same commit where you upgrade to Lagom 1.5.0 or you can upgrade your deployment process on a first step (still using Lagom 1.4.x) and later upgrade to Lagom 1.5.0.

See Deployment below.

## Deployment

ConductR tooling and Lightbend Orchestration handled all the required pieces to deploy on ConductR and Kubernetes or DC/OS. Lagom 1.5.0 only supports a manually maintained deployment process.

In particular, ConductR tooling and Lightbend Orchestration handled the following:

1. extend the your application with: cluster bootstrapping, service location, akka management and health checks
2. setup and produce docker images
3. prepare the deployment specs for the target orchestrator

Regarding `1.`, starting with Lagom 1.5 your application will include [[Akka management HTTP|Cluster#Akka-Management]] out of the box with [[health checks|Cluster#Health-Checks]] enabled by default. Cluster formation also supports [[Cluster Bootstrapping|Cluster#Joining-during-production-(Akka-Cluster-Bootstrap)]] as a new way to form a cluster. These new defaults may require at least two changes on your codebase. First, if you want to opt-in to cluster bootstrapping you must make sure you don't set `seed-nodes`. Second, if you use Cluster Bootstrapping, you will have to setup a [[discovery|Cluster#Akka-Discovery]] mechanism (see the [[reference guide|Cluster#Akka-Discovery]] for more details).

Regarding docker images and deployment specs, once you remove external tooling you will have to setup and maintain them manually. Instead of producing the `Dockerfile`, deployment scripts produced by tooling and orchestration specs from scratch, use the tooling to create these files and add them to git. You can later review and maintain them at will.

For example, using `docker:stage` on your project you will generate `<project-name>/target/docker/stage/Dockerfile` and other files required to build the image. You may use these files directly or have them as a guide to tune the `sbt-native-packager` plugin to produce a similar `Dockerfile`.

Similarly, with a docker image produced with Lightbend Orchestration still enabled, you may use `rp generate-kubernetes-resources …` to produce Kubernetes YAML files that you can keep under SCM.

### Cluster Formation

A new mechanism to form and [[join an Akka Cluster|Cluster#Joining]] is introduced in Lagom 1.5. Apart from the original `Manual Cluster Formation` the new `Akka Cluster Bootstrap` is now supported. This new mechanism is introduced with lower precedence than `Manual Cluster Formation` so if you rely on the use of a list of `seed-nodes` then everything will work as before. On the other hand, `Akka Cluster Bootstrap` takes precedence over the `join-self` cluster formation for single-node clusters. If you use single-node clusters via `join-self` you will have to explicitly disable `Akka Cluster Bootstrap`:

```
lagom.cluster.bootstrap.enabled = false
```

### Service Discovery

When opting in to Akka Cluster Bootstrapping as a mechanism for Cluster formation you will have to setup a [[Service Discovery|Cluster#Akka-Discovery]]  method for nodes to locate each other.

## Upgrading a production system

TODO

### Rolling upgrade

TODO

### Downtime upgrade

TODO
