# Lagom 1.5 Migration Guide

This guide explains how to migrate from Lagom 1.4 to Lagom 1.5. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.5 also updates to the latest major versions of Play (2.7) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.7. Please refer to the Play 2.7 migration guide (TODO) and the [Akka HTTP 10.1.0 release announcement](https://akka.io/blog/news/2018/03/08/akka-http-10.1.0-released.html) for more details.


## Build changes

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.5.0-M2")
```


## Deprecations

TODO


## ConductR

ConductR is no longer supported with Lagom 1.5.

We recommend migrating to Kubernetes, DC/OS or another deployment solution before upgrading.

[Lighbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) is an open-source suite of tools that helps you deploy Lagom services to Kubernetes and DC/OS. It provides an easy way to create Docker images for your applications and introduces an automated process for generating Kubernetes and DC/OS resource and configuration files for you from those images. This process helps reduce the friction between development and operations. If you are using Kubernetes or DC/OS, or interested in trying one of these platforms, we encourage you to read the Lightbend Orchestration documentation to understand how to use it with Lagom and other components of the [Lightbend Reactive Platform](https://www.lightbend.com/products/reactive-platform).

If you are not using Kubernetes or DC/OS, you must configure your services in a way that suits your production environment. See [[Running Lagom in production|ProductionOverview]] for more information.


## Lightbend Orchestration

TODO (new version required)


## Upgrading a production system

TODO

### Rolling upgrade

TODO

### Downtime upgrade

TODO
