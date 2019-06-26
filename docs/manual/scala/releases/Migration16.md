# Lagom 1.6 Migration Guide

This guide explains how to migrate from Lagom 1.5 to Lagom 1.6. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.6 updates to the latest major versions of Play (2.8), Akka (2.6) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.8. Please refer to the [Play 2.8 migration guide](https://www.playframework.com/documentation/2.8.0-M1/Migration28), [Akka Migration Guide 2.5.x to 2.6.x](https://doc.akka.io/docs/akka/2.6/project/migration-guide-2.5.x-2.6.x.html) and the [Akka HTTP 10.1.x release announcements](https://akka.io/blog/news-archive.html) for more details.

For a detailed list of version upgrades of other libraries Lagom builds on such as for Slick, Kafka and others, refer to the [release notes](https://github.com/lagom/lagom/releases).

## Migrating from Lagom 1.5

To migrate from Lagom 1.5 we recommend first migrating to latest version of Lagom 1.5 before upgrading to Lagom 1.6. Refer to the [release notes](https://github.com/lagom/lagom/releases) for details upgrading to latest version of Lagom 1.5.

## Build changes

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.0")
```

We also recommend upgrading to sbt 1.2.8 or later, by updating the `sbt.version` in `project/build.properties`.

## Main changes

### Shard Coordination

Since Lagom `1.4.0` users might opt into the `ddata` coordination of shards while `persistence`-based shard coordination remained the default. Lagom `1.6.0` changes this default. This means that if you had:

```HOCON
# Using 'ddata' before Lagom 1.6.0
akka.cluster.sharding.state-store-mode = ddata
```

You no longer need the setting and can simply use Lagom's default.

If your cluster is currently using Lagom defaults (`persistence`) then upgrading to `ddata` will require a full cluster shutdown. If you want to avoid the full service shutdown when doing the upgrade you can still use `persistence` if you enable it explicitly using:

```HOCON
# Opting out of 'ddata' to use 'persistence' since Lagom 1.6.0
akka.cluster.sharding.state-store-mode = persistence
```

## Minor changes

### JSON Compression threshold

When creating a serializer with `JsonSerializer.compressed[T]` compression will only kick in when the serialized representation is biger than a threshold. The default value for `lagom.serialization.json.compress-larger-than` has been increased from 1024 bytes to 32 Kilobytes. (See [#1983](https://github.com/lagom/lagom/pull/1983))

## Cluster Shutdown changes

This is a summary of changes in Lagom that would require a full cluster shutdown.

- Lagom 1.6 changed the shard coordination strategy default. Read the [[Shard Coordination|Migration16#Shard-Coordination] section for details on how to opt back to Lagom 1.5 behavior to support rolling upgrades.

