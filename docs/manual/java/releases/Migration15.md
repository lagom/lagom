# Lagom 1.5 Migration Guide

This guide explains how to migrate from Lagom 1.4 to Lagom 1.5. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.5 also updates to the latest major versions of Play (2.7), Akka (2.5.21) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.7. Please refer to the Play 2.7 migration guide (https://www.playframework.com/documentation/2.7.0/Migration27), [Akka 2.5.21 release announcement](https://akka.io/blog/news/2019/02/13/akka-2.5.21-released) and the [Akka HTTP 10.1.x release announcements](https://akka.io/blog/news-archive.html) for more details. Also, review the [list of most important changes since 0.22](https://doc.akka.io/docs/akka-stream-kafka/current/release-notes/1.0.x.html#most-important-changes-since-0-22) on the Alpakka-Kafka connector is you use Kafka from Lagom.

For a detailed list of version upgrades of other libraries Lagom builds on such as for Slick, Kafka and others, refer to the [release notes](https://github.com/lagom/lagom/releases).

## Migrating from Lagom 1.4

To migrate from Lagom 1.4 we recommend first migrating to latest version of Lagom 1.4 (currently `1.4.11`) before upgrading to Lagom 1.5. Refer to the [release notes](https://github.com/lagom/lagom/releases) for details upgrading to latest version of Lagom 1.4.
## Build changes

### Maven

If you're using a `lagom.version` property in the `properties` section of your root `pom.xml`, then simply update that to `1.5.0`. Otherwise, you'll need to go through every place that a Lagom dependency, including plugins, is used, and set the version there.

> **Note:** Lagom 1.5 requires, at least,  Maven `3.6.0`. Please update your environments.

### sbt

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.5.0")
```

We also recommend upgrading to sbt 1.2.8 or later, by updating the `sbt.version` in `project/build.properties`.

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

## Cluster Formation

A new mechanism to form and [[join an Akka Cluster|Cluster#Joining]] is introduced in Lagom 1.5. Apart from the original `Manual Cluster Formation` the new `Akka Cluster Bootstrap` is now supported. This new mechanism is introduced with lower precedence than `Manual Cluster Formation` so if you rely on the use of a list of `seed-nodes` then everything will work as before. On the other hand, `Akka Cluster Bootstrap` takes precedence over the `join-self` cluster formation for single-node clusters. If you use single-node clusters via `join-self` you will have to explicitly disable `Akka Cluster Bootstrap`:

```
lagom.cluster.bootstrap.enabled = false
```

## Service Discovery

When opting in to Akka Cluster Bootstrapping as a mechanism for Cluster formation you will have to setup a [[Service Discovery|Cluster#Akka-Discovery]]  method for nodes to locate each other.

## ConductR

ConductR is no longer supported with Lagom 1.5.

We recommend migrating to Kubernetes, DC/OS or another deployment solution before upgrading. If you are using ConductR in production and need assistance migrating to other solutions please [contact Lightbend](https://www.lightbend.com/contact).

See Deployment below.

## Lightbend Orchestration

[Lightbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) is not supported with Lagom 1.5. If you used Lightbend Orchestration for your deployment you will have to migrate to a manual process. This migration may happen on the same commit where you upgrade to Lagom 1.5.0 or you can upgrade your deployment process on a first step (still using Lagom 1.4.x) and later upgrade to Lagom 1.5.0.

See Deployment below.

## Deployment

ConductR tooling and Lightbend Orchestration handled all the required pieces to deploy on ConductR and Kubernetes or DC/OS. Lagom 1.5.0 only supports a manually maintained deployment process.

In particular, ConductR tooling and Lightbend Orchestration handled some or all of the following:

1. extending the application with: cluster bootstrapping, akka management and health checks
2. Service Location
3. setting up and producing docker images
4. preparing the deployment specs for the target orchestrator
5. Secrets

#### Application extensions

Starting with Lagom 1.5 your application will include [[Akka management HTTP|Cluster#Akka-Management]] out of the box with [[health checks|Cluster#Health-Checks]] enabled by default.  Akka management HTTP is a supporting tool for health checks, cluster bootstrap and a few other new features in Lagom 1.5. 

Cluster formation now also supports [[Cluster Bootstrapping|Cluster#Joining-during-production-(Akka-Cluster-Bootstrap)]] as a new way to form a cluster. 

These new defaults may require at least two changes on your codebase. First, if you want to opt-in to cluster bootstrapping you must make sure you don't set `seed-nodes`. `seed-nodes` always takes precedence over any other cluster formation mechanism. Second, if you use Cluster Bootstrapping, you will have to setup a [discovery](https://doc.akka.io/docs/akka/2.5/discovery/index.html) mechanism (see the [[Lagom Cluster reference guide|Cluster#Akka-Discovery]] for more details). 

#### Service Location

You no longer have a `ServiceLocator `provided by the tooling libraries so you will have to provide one of your choice. We recommend using the new [`lagom-akka-discovery-service-locator`](https://github.com/lagom/lagom-akka-discovery-service-locator) which is implemented using [Akka Service Discovery](https://doc.akka.io/docs/akka/current/discovery/index.html) implementations.

Read the [docs](https://github.com/lagom/lagom-akka-discovery-service-locator) of the new `lagom-akka-discovery-service-locator` for details on how to setup the Akka Service Discovery [method](https://doc.akka.io/docs/akka/current/discovery/index.html). For example, 

```
akka {
  discovery {
   method = akka-dns
  }
}
```

#### Docker images and deployment specs

With the removal of ConductR or Lightbend Orchestration, the docker images and deployment specs will have to be maintained manually. Therefore the recommended migration is to take ownership of the `Dockerfile`, deployment scripts and orchestration specs created by such tooling, such as by committing them to the source repository and then maintaining them.  We also found that such maintenance can be made easier by making use of [kustomize](https://github.com/kubernetes-sigs/kustomize).

For example, using `docker:stage` on your project you will generate `<project-name>/target/docker/stage/Dockerfile` and other files required to build the image. You may use these files directly or have them as a guide to tune the `sbt-native-packager` plugin to produce a similar `Dockerfile`.

Similarly, with a docker image produced with Lightbend Orchestration still enabled, you may use `rp generate-kubernetes-resources …` to produce Kubernetes YAML files that you can keep under SCM.

#### Secrets

Lightbend Orchestration supported declaring [secrets](https://developer.lightbend.com/docs/lightbend-orchestration/current/features/secrets.html) on `build.sbt`  which user's code could then read from a file in the pod. Starting from Lagom 1.5 there is no specific support for secrets and the recommendation is to use the default option suggested by each target orchestrator. For example, when deploying to Kubernetes or OpenShift [declare the secret as an environment variable](https://kubernetes.io/docs/concepts/configuration/secret/#using-secrets-as-environment-variables) on your `Deployment` and inject the environment variable in your `application.conf`. For example:

```
my-database {
  password = "${DB_PASSWORD}"
}
```

## Upgrading a production system

TODO

### Rolling upgrade

TODO

### Downtime upgrade

TODO
