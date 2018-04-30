# Lagom 1.3 Migration Guide

This guide explains how to migrate from Lagom 1.2 to Lagom 1.3.

## Build changes

### Maven

If you're using a `lagom.version` property in the `properties` section of your root `pom.xml`, then simply update that to `1.3.2`. Otherwise, you'll need to go through every place that a Lagom dependency, including plugins, is used, and set the version there.

### sbt

It is not required, but is recommended, that you upgrade to sbt 0.13.13.  To upgrade an existing project to sbt 0.13.13, open `project/build.properties`, and edit the sbt version to be 0.13.13, for example:

```
sbt.version=0.13.13
```

When creating new projects, the use of Activator is now deprecated. Instead, you should use sbt 0.13.13's `new` support. This requires upgrading your sbt launcher to 0.13.13. This can be done by downloading and installing it from [the sbt website](https://www.scala-sbt.org/download.html).

Once you have upgraded sbt, you then need to upgrade Lagom. This can be done by editing the Lagom plugin version in `project/plugins.sbt`, for example:

```scala
addSbtPlugin("com.lightbend.lagom" % "sbt-plugin" % "1.3.2")
```

If using ConductR with sbt, you should upgrade the ConductR sbt plugin to at least `2.3.4`.

In addition, when importing external Lagom projects into the Lagom development environment, `lagomExternalProject` is now deprecated, and should be replaced with `lagomExternalJavadslProject` or `lagomExternalScaladslProject`.

## Service Info

It is now compulsory for Play applications to provide a [[ServiceInfo|ServiceInfo]] programmatically to run in the development environment. If you were providing `lagom.play.service-name` and `lagom.play.acls` via `application.conf` in your Play application, the development mode's `runAll` will still work, but this is now deprecated. If your Play app does not currently include a Guice module, you should add one and use the new `bindServiceInfo` method to configure the service name and ACLs that you wish to expose to the service gateway. The suggested location is `<play_app_folder>/app/Module.java` so that Guice will find it automatically.

