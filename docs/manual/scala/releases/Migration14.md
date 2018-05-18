# Lagom 1.4 Migration Guide

This guide explains how to migrate from Lagom 1.3 to Lagom 1.4. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.4 also updates to the latest major versions of Play (2.6) and Akka (2.5). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses the Play and Akka APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.6. Please refer to the [Play 2.6 migration guide](https://www.playframework.com/documentation/2.6.x/Migration26) and the [Akka 2.5 migration guide](https://doc.akka.io/docs/akka/current/project/migration-guide-2.4.x-2.5.x.html?language=scala) for more details.


## Build changes

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin, for example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.4.0")
```

Lagom 1.4.0 also requires Sbt 0.13.16 or later (recommended sbt 1.x). If your existing project is using a previous version of Sbt, you will need to upgrade it by editing the `project/build.properties` file. For example:

```
sbt.version=1.1.5
```

We also recommend you [upgrade](https://www.scala-sbt.org/download.html) your `sbt` launcher.



## Scala 2.12 support

Lagom is now cross compiled to Scala 2.11 and 2.12. It's recommended to upgrade to Scala 2.12 whenever possible. The Scala 2.12 version of Lagom benefits from the improved optimizer and use of Java 8 features in the new version of the Scala compiler, resulting in a leaner, faster Lagom for everyone.

The Scala version can be updated by editing the `build.sbt` file, and updating the `scalaVersion` settings, for example:

```scala
scalaVersion in ThisBuild := "2.12.4"
```


## Akka HTTP as the default server engine

Play 2.6 introduces a new default server engine implemented using [Akka HTTP](https://doc.akka.io/docs/akka-http/current/?language=scala) instead of Netty.

You can read more in the Play documentation at [Akka HTTP Server Backend](https://www.playframework.com/documentation/2.6.x/AkkaHttpServer).

Lagom 1.4 now defaults to using the Akka HTTP server when using sbt. Once you upgrade the version of the Lagom sbt plugin in `project/plugins.sbt`, you won't need to make any other changes to use it.

If you need to change back to Netty, you have to explicitly disable the `LagomAkkaHttpServer` plugin and enable the `LagomNettyServer` plugin. Note that the `LagomAkkaHttpServer` plugin is added by default to any `LagomJava` or `LagomScala` project.

```scala
lazy val `inventory-service-impl` = (project in file("inventory-impl"))
  .enablePlugins(LagomScala, LagomNettyServer) // Adds LagomNettyServer
  .disablePlugins(LagomAkkaHttpServer)         // Removes LagomAkkaHttpServer
  .settings( /* ... */ )
  .dependsOn(`inventory-api`)
```


## Play JSON changes

Play JSON has been extracted from the main Play project into its own [repository](https://github.com/playframework/play-json). The version number has been decoupled from the Play framework version number, as the two are now released independently of each other. This change should be transparent to most Lagom Scala users, but there are some API changes that may require updates in your services:

- Uses of `play.api.data.validation.ValidationError` have changed to a new class: `play.api.libs.json.JsonValidationError`. If you have defined a custom validation, you'll need to update them to use the new `JsonValidationError` class.
- The syntax for array lookups in JSON path expressions has changed. See [the Play 2.6 migration guide](https://www.playframework.com/documentation/2.6.x/Migration26#JSON-array-index-lookup) for details.


## Using `LagomServiceClientComponents` in Play applications

If you have mixed [`LagomServiceClientComponents`](https://www.lagomframework.com/documentation/1.4.x/scala/api/com/lightbend/lagom/scaladsl/client/LagomServiceClientComponents.html) directly into a Play application that communicates with Lagom services, you will need to also mix in [`LagomConfigComponent`](https://www.lagomframework.com/documentation/1.4.x/scala/api/com/lightbend/lagom/scaladsl/api/LagomConfigComponent.html).


## Deprecations

### Binding services

Binding multiple Lagom service descriptors in one Lagom service has been deprecated. If you are currently binding multiple Lagom service descriptors in one Lagom service, you should combine these into one. The reason for this change is that we found most microservice deployment platforms simply don't support having multiple names for the one service, hence a service that serves multiple service descriptors, each with their own name, would not be compatible with those environments.

Consequently, we have deprecated the methods for binding multiple service descriptors. To migrate, in your application cake that binds your services, change the following code:

```scala
lazy val lagomServer = LagomServer.forServices(bindService[ItemService].to(wire[ItemServiceImpl]))
```

to:

```scala
lazy val lagomServer = serverFor[ItemService](wire[ItemServiceImpl])
```

The change to drop support for multiple services will require another code update on your `Application` class. Before you had to override `describeServices` like this:

```scala
  override def describeServices = List(
    readDescriptor[ItemService]
  )
```

That is now deprecated and will issue a warning on runtime (unfortunately Scala will not cause a compilation warning because of the deprecation). The method replacing `describeServices` is `describeService` (in singular) and it will take an `Option[Descriptor]` instead of a list:

```scala
  override def describeService = Some(readDescriptor[ItemService])
```


## Cassandra persistence

Lagom 1.4 requires each service that uses Cassandra persistence to define three keyspace configuration settings in `application.conf`:

```conf
cassandra-journal.keyspace = my_service_journal
cassandra-snapshot-store.keyspace = my_service_snapshot
lagom.persistence.read-side.cassandra.keyspace = my_service_read_side
```

While different services should be isolated by using different keyspaces, it is perfectly fine to use the same keyspace for all of these components within one service. In that case, it can be convenient to define a custom keyspace configuration property and use [property substitution](https://github.com/typesafehub/config#factor-out-common-values) to avoid repeating it.

```conf
my-service.cassandra.keyspace = my_service

cassandra-journal.keyspace = ${my-service.cassandra.keyspace}
cassandra-snapshot-store.keyspace = ${my-service.cassandra.keyspace}
lagom.persistence.read-side.cassandra.keyspace = ${my-service.cassandra.keyspace}
```

If you are using macOS or Linux, or have other access to a bash scripting environment, running this shell script in your base project directory can help add this configuration to existing services:

```bash
#!/bin/bash

set -eu

for svc_dir in *-impl; do
    svc=${svc_dir%-impl}

    # change $svc_dir to $svc on the next line to leave out the _impl suffix
    keyspace=$(tr - _ <<<$svc_dir)
    cat >> $svc_dir/src/main/resources/application.conf <<END
$svc.cassandra.keyspace = $keyspace

cassandra-journal.keyspace = \${$svc.cassandra.keyspace}
cassandra-snapshot-store.keyspace = \${$svc.cassandra.keyspace}
lagom.persistence.read-side.cassandra.keyspace = \${$svc.cassandra.keyspace}
END
done
```

Previous versions of Lagom automatically calculated a default Cassandra keyspace for each service, based on the name of the service project, and injected this keyspace configuration in development mode. When running in production, these calculated keyspaces were not used, resulting in multiple services sharing the same keyspaces by default.

In Lagom 1.4, services that use Cassandra persistence will fail on startup when these properties are not defined.

See [[Storing Persistent Entities in Cassandra|PersistentEntityCassandra#Configuration]] for more details.


## RDBMS persistence

If you are using Lagom's Persistence API with a relational database, you will need to add an index to your journal table.

The relational database support is based on the [`akka-persistence-jdbc`](https://github.com/dnvriend/akka-persistence-jdbc) plugin. This plugin was updated to version 3.1.0, which includes an important [bug fix](https://github.com/dnvriend/akka-persistence-jdbc/issues/96) that requires a new column index. If you do not update your database schema, it will result in degraded performance when querying events.

Below you will find the index creation statement for each supported database.

### Postgres

```sql
CREATE UNIQUE INDEX journal_ordering_idx ON public.journal(ordering);
```

### MySQL

```sql
CREATE UNIQUE INDEX journal_ordering_idx ON journal(ordering);
```

### Oracle

```sql
CREATE UNIQUE INDEX "journal_ordering_idx" ON "journal"("ordering")
```

### H2 Database (for use in development only)

```sql
CREATE UNIQUE INDEX "journal_ordering_idx" ON PUBLIC."journal"("ordering");
```

From version 3.0.0, the `akka-persistence-jdbc` Persistence Query implementation treats the offset as exclusive instead of inclusive, matching the behavior of the Cassandra implementation. Most Lagom users will not be affected by this change. Previous versions of Lagom compensated for the different behavior of the Cassandra and JDBC implementations automatically, and this change allowed that workaround to be removed. This will only impact you if you were using the Akka Persistence Query API directly.

The new version of `akka-persistence-jdbc` has a backward-incompatible change to the `ReadJournalDao` API. This is not being used by Lagom and most Lagom users will not be impacted by this. However, if you have implemented a DAO extending `ReadJournalDao`, you will need to migrate your code manually. Details can be found on GitHub in the [pull request containing the change](https://github.com/dnvriend/akka-persistence-jdbc/pull/148).


## Development mode Service Locator

In previous versions, Lagom's development mode Service Locator has listened on port 8000. Because port 8000 is a common port on which apps listen, this caused conflicts for some Lagom users. In Lagom 1.4, its default port has been changed to 9008. This can be changed if needed in your build configuration. See the documentation on the [[Service Locator default port|ServiceLocator#Default-port]] for details.


## Upgrading a production system

Lagom 1.4 introduces a few new features and changes that you must be aware before upgrading a clustered production system. Note, this is only relevant if you are using clustering. Clustering is enabled in Lagom when using **Persistence** or **PubSub** APIs, or if you have used it directly.

### Background information

Akka 2.5 introduces a new state storage mode for sharding data, which is a feature used by the **Persistence** layer. This new sharding state storage mode is based on *Conflict Free Replicated Data Types (CRDTs)* and it's named `distributed-data`, `ddata` for short. This is **incompatible** with the previous mode (`persistence`). Mixing modes in a cluster will corrupt your event journal. Therefore, we are keeping the `persistence` mode as the default in Lagom.

For more information about the state storage mode, see [Distributed Data vs. Persistence Mode](https://doc.akka.io/docs/akka/current/cluster-sharding.html#distributed-data-vs-persistence-mode) in the Akka documentation.

Moreover, some of the internal messages used by Lagom have new serializers. Special attention must be taken when performing rolling upgrades as old nodes in the cluster may not be able to deserialize some messages.

Depending on whether you are planning a rolling upgrade or downtime upgrade, you will need to take different actions.

### Rolling upgrade

Rolling upgrades can be safely performed if, and only if, you migrate your cluster from Lagom 1.3.10 to Lagom 1.4. If your system is using any previous version of Lagom, you will first need to upgrade it to 1.3.10. Make sure you read and understand any intermediary migration guide.

As mentioned above, Lagom 1.4 is not using the new `ddata` mode for sharding data storage and some new messages serializers are disabled. This will allow you to perform rolling upgrades without any risk (assuming your current version is 1.3.10).

Other than that difference, refer to the [Akka Rolling Update](https://doc.akka.io/docs/akka/current/project/migration-guide-2.4.x-2.5.x.html?language=scala#rolling-update) documentation for more detailed information on important considerations when performing a rolling upgrade.

### Downtime upgrade

If your application can tolerate a *one time only* downtime upgrade, we recommend you to enable `ddata` and the new serializers for `akka.Done`, `akka.actor.Address` and `akka.remote.UniqueAddress`. Once this upgrade is complete, further downtime is not required.

In order to achieve this, make sure you have added the following properties to your `application.conf` file.

```
# Enable new sharding state store mode by overriding Lagom's default
akka.cluster.sharding.state-store-mode = ddata

# Enable serializers provided in Akka 2.5.8+ to avoid the use of Java serialization.
akka.actor.serialization-bindings {
    "akka.Done"                 = akka-misc
    "akka.actor.Address"        = akka-misc
    "akka.remote.UniqueAddress" = akka-misc
}
```

## ConductR

ConductR users must update to `conductr-lib` 2.1.1 or later (2.2.0 recommended) for full compatibility with Lagom 1.4.0.

You can find more information in the [`conductr-lib` README file](https://github.com/typesafehub/conductr-lib/blob/master/README.md).

Edit the `project/plugins.sbt` file to update `sbt-conductr` to version 2.5.1 or later (2.6.0 recommended):

```scala
addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % "2.6.0")
```

This automatically includes the correct version of `conductr-lib`.
