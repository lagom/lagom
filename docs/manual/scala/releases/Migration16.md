# Lagom 1.6 Migration Guide

This guide explains how to migrate from Lagom 1.5 to Lagom 1.6. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.6 updates to the latest major versions of Play (2.8), Akka (2.6) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.8. Please refer to the [Play 2.8 migration guide](https://www.playframework.com/documentation/2.8.x/Migration28), [Akka Migration Guide 2.5.x to 2.6.x](https://doc.akka.io/docs/akka/2.6/project/migration-guide-2.5.x-2.6.x.html) and the [Akka HTTP 10.1.x release announcements](https://akka.io/blog/news-archive.html) for more details.

For a detailed list of version upgrades of other libraries Lagom builds on such as for Slick, Kafka and others, refer to the [release notes](https://github.com/lagom/lagom/releases).

## Migrating from Lagom 1.5

To migrate from Lagom 1.5 we recommend first migrating to latest version of Lagom 1.5 before upgrading to Lagom 1.6. Refer to the [release notes](https://github.com/lagom/lagom/releases) for details upgrading to latest version of Lagom 1.5.

## Build changes

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.0")
```

Lagom 1.6 requires sbt 1.3.2 or later, upgrade your version by updating the `sbt.version` in `project/build.properties`.

## Main changes

### Remoting Artery

Lagom 1.6.0 builds on Akka 2.6.0 that uses a new Akka Remote implementation called Artery. Artery is enabled by default in Lagom and replaces the previous Akka Remote protocol (aka. Akka Remote Classic). If you are using Lagom in a clustered setup, you will need to shutdown all nodes before updating, unless you choose to disable Artery.

To use classic remoting instead of Artery, you need to:

1. Set property `akka.remote.artery.enabled` to `false`. Further, any configuration under `akka.remote` that is specific to classic remoting needs to be moved to `akka.remote.classic`. To see which configuration options are specific to classic search for them in: [`akka-remote/reference.conf`](https://github.com/akka/akka/blob/master/akka-remote/src/main/resources/reference.conf)
2. Add Netty dependency as explained in [Akka Remoting docs](https://doc.akka.io/docs/akka/2.6/remoting.html#dependency):

```scala
libraryDependencies += "io.netty" % "netty" % "3.10.6.Final"
```

### Shard Coordination

In Lagom 1.4 and 1.5 users could use the `akka.cluster.sharding.store-state-mode` configuration key to switch from the default `persistence`-based shard coordination to the `ddata`-based coordination.  As of Lagom 1.6 `ddata` is the new default.

Switching from `persistence` to `ddata`, such as if your cluster relies of Lagom's default configuration, will require a full cluster shutdown. Therefore, if you want to avoid the full service shutdown when migrating to Lagom 1.6 you need to explicitly opt-back to `persistence`, as such:

```HOCON
# Opt-back to Lagom 1.5's 'persistence' instead of Lagom 1.6's default of 'ddata'.
akka.cluster.sharding.state-store-mode = persistence
```

### Akka Persistence Cassandra Update

The Akka Persistence Cassandra plugin is updated to version 0.101. This version requires two schema migrations before you upgrade to Lagom 1.6.0. For more information on how to migrate, consult the following documentations:

* [Akka Persistence Cassandra Migrations to 0.101](https://doc.akka.io/docs/akka-persistence-cassandra/0.101/migrations.html#migrations-to-0-101)
* [Akka Persistence Cassandra Migrations to 0.80](https://doc.akka.io/docs/akka-persistence-cassandra/0.101/migrations.html#migrations-to-0-80-and-later)

Note that although it's technically possible to run the migrations while running your application we advise against it.

### Akka Persistence Typed

With the new support for Akka Persistence Typed you may [[migrate your Persistent Entities to Akka Persistence Typed|MigratingToAkkaPersistenceTyped]] `Behavior`'s.

## Upgrading a production system

As usual, before upgrading to Lagom 1.6.0, make sure you are using the latest version on the 1.5.x series.

During a rolling upgrade your [[Projections]] may experience a degraded behavior. The service will self-heal when the rolling upgrade completes. Some internal messages taking care of the distribution of the worker instances of your projection have changed. As a consequence,  your old nodes won't be able to gossip with the new ones but as soon as the rolling upgrade completes, all nodes will be on the same version of your service the projection will operate normally.

Lagom 1.6.0 has a few new default settings that will prevent you to run a rolling upgrade. In case you prefer to run a rolling upgrade, you will need to opt-out from each of these new defaults as explained below.

This is a summary of changes in Lagom 1.6 that would require a full cluster shutdown rather than a rolling upgrade:

* The change in [[Akka Remote|Migration16#Remoting-Artery]] default implementation.
* The change in default [[Shard Coordination|Migration16#Shard-Coordination]] strategy.
* The change in [[Cassandra plugin version|Migration16#Akka-Persistence-Cassandra-Update]]. Only impacts Lagom applications using Cassandra.

Finally, if you [[migrate your Persistent Entities as Akka Persistence Typed|MigratingToAkkaPersistenceTyped]] `Behavior`'s you will also need a cluster shutdown for the upgrade.

## Minor changes

### JSON Compression threshold

When creating a serializer with `JsonSerializer.compressed[T]` compression will only kick in when the serialized representation is biger than a threshold. The default value for `lagom.serialization.json.compress-larger-than` has been increased from 1024 bytes to 32 Kilobytes. (See [#1983](https://github.com/lagom/lagom/pull/1983))
