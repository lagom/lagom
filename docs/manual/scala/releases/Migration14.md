# Lagom 1.4 Migration Guide

This guide explains how to migrate from Lagom 1.3 to Lagom 1.4. If you are upgrading from an earlier version, be sure to review previous migration guides.

## Build changes

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.4.0-M3")
```

## API and configuration changes

### Backward-incompatible API changes

The return types of the method below were changed, which could result in deprecation warnings:

* Typesafe config is now used instead of Play config in `AdditionalConfiguration.configuration`, see [881](https://github.com/lagom/lagom/pull/881)
* The return types of `LagomServerBuilder.buildRouter`, `LagomServerBuilder.router`, and `LagomServer.router` were changed to be strongly typed from `Router` to `LagomServiceRouter` in [888](https://github.com/lagom/lagom/pull/888)
* `PlayJsonSerializer` serialization of Non-`JsObject`s was fixed in [1071](https://github.com/lagom/lagom/pull/1071), changing the return type of `JsonMigration.transfrorm` from `JsObject` to `JsValue`

### Upgrading to Play 2.6 and Akka 2.5

The Play and Akka versions in Lagom were updated to the latest major verions. In case you are using either of them directly, you may need do  changes in your code if you are using either of them directly. Please refer to the [Play 2.6 migration guide](https://www.playframework.com/documentation/2.6.x/Migration26) and the [Akka 2.5 migration guide](https://doc.akka.io/docs/akka/current/project/migration-guide-2.4.x-2.5.x.html?language=scala) for more details.


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

That is now deprecated and will issue a warning on runtime (unfortunately scala 2.11 will not cause a compilation warning because of the deprecation). The method replacing `describeServices` is `describeService` (in singular) and it will take an `Option[Descriptor]` instead of a list:

```scala
  override def describeService = Some(readDescriptor[ItemService])
```


### Configuring Cassandra keyspaces

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

### Relational Databases - Akka Persistence JDBC

If you are using Lagom's `Persistent Entity` API with a relational database, you will need to add an index to your journal table.

The relational database support is based on `akka-persistence-jdbc` plugin. The plugin was updated to version 3.1.0, which include an important [bug fix](https://github.com/dnvriend/akka-persistence-jdbc/issues/96) that requires a new column index. Failing in updating your database schema will result in degraded performance when querying events.

Bellow you will find the index creation statement for each supported database.

#### Postgres

```sql
CREATE UNIQUE INDEX journal_ordering_idx ON public.journal(ordering);
```

#### MySQL

```sql
CREATE UNIQUE INDEX journal_ordering_idx ON journal(ordering);
```

#### Oracle

```sql
CREATE UNIQUE INDEX "journal_ordering_idx" ON "journal"("ordering")
```

#### H2 Database (for use in development only)

```sql
CREATE UNIQUE INDEX "journal_ordering_idx" ON PUBLIC."journal"("ordering");
```

Moreover, in `akka-persistence-jdbc` 3.1.x series, the `Events` query treats the offset as exclusive instead of inclusive. In general, this should not be a problem. Previous versions of Lagom had a workaround for it and this change in behavior should be transparent. This will only impact you if you were using the `Akka Persistence Query` directly.

In addition to that, this new plugin version removed the dependency on `JournalRow` in `ReadJournalDao`. This is a breaking change for everyone who implements a custom `ReadJournalDao`. Note, this is not being used by Lagom and Lagom users are, in principle, not impacted by this. However, if for some reason you have implemented a DAO extending the plugin's `ReadJournalDao`, you will need to migrate your code manually. More details can be found [here](https://github.com/dnvriend/akka-persistence-jdbc/pull/148).

### Default Service Locator port

Historically, Lagom's service locator has listened on port 8000. Because port 8000 is a common port on which apps listen, its default value has been changed to 9008.

### HTTP Backend


Play 2.6 introduces a new HTTP backend implemented using Akka HTTP instead of Netty. This switch on the HTTP backend is part of an ongoing effort to replace all building blocks in Lagom for an Akka-based equivalent. Note that when consuming HTTP services, Lagom's Client Factory still relies on a Netty-based Play-WS instance.

If you want to use the new Akka HTTP you won't need to change anything. Once you upgrade the version of the Lagom sbt plugin in `project/plugins.sbt` any new build will use Akka HTTP.

You can opt-out of Akka HTTP to use Netty: in `sbt` you have to explicitly disable the `LagomAkkaHttpServer` plugin and enable the `LagomNettyServer` plugin. Note that the `LagomAkkaHttpServer` plugin is added by default on any `LagomJava` or `LagomScala` project.

```scala
lazy val `inventory-service-impl` = (project in file("inventory-impl"))
  .enablePlugins(LagomScala, LagomNettyServer) // Adds LagomNettyServer
  .disablePlugins(LagomAkkaHttpServer)         // Removes LagomAkkaHttpServer
  .settings( /* ... */ )
  .dependsOn(`inventory-api`)
```


## Upgrading a production system

Lagom 1.4 introduces a few new features and changes that you must be aware before upgrading a clustered production system. Note, this is only relevant if you are using clustering. Clustering is enabled in Lagom when using **Persistence** or **PubSub** APIs, or if you have used it directly.

### Background information
Akka 2.5 introduces a new state storage mode for sharding data, which is a feature used by the **Persistence** layer. This new sharding state storage mode is based on *Conflict Free Replicated Data Types (CRDTs)* and it's named `distributed-data`, `ddata` for short. This is **incompatible** with the previous mode (`persistence`). Mixing modes in a cluster will corrupt your event journal. Therefore, we are keeping the `persistence` mode as the default in Lagom.
For more information over state storage mode, see [Distributed Data vs. Persistence Mode](https://doc.akka.io/docs/akka/current/cluster-sharding.html#distributed-data-vs-persistence-mode).

Moreover, some of the internal messages used by Lagom have new serializers. Special attention must be taken when performing rolling upgrades as old nodes in the cluster may not be able to deserialize some messages.

Depending if you are planing a rolling upgrade or downtime upgrade, we will need to take different actions.

### Rolling upgrade

Rolling upgrades can be safely performed if, and only if, you migrate your cluster from Lagom 1.3.10 to Lagom 1.4. If your system is using any previous version of Lagom, you will first need to upgrade it to 1.3.10. Make sure you read and understand any intermediary migration guide.

As mentioned above, Lagom 1.4 is not using the new `ddata` mode for sharding data storage and some new messages serializers are disabled. This will allow you to perform rolling upgrades without any risk (assuming your current version is 1.3.10).

### Downtime upgrade

If your application can tolerate downtime, we recommend you to enable `ddata` and the new serializer for `akka.Done`.

In order to achieve this, make sure you have added the following properties to your `application.conf` file.

```
# Enable new sharding state store mode by overriding Lagom's default
akka.cluster.sharding.state-store-mode = ddata

# Enable the serializer for akka.Done provided in Akka 2.5.8+ to avoid the use of Java serialization.
akka.actor.serialization-bindings {
  "akka.Done" = akka-misc
}
```

## ConductR

ConductR users must update to `conductr-lib` 2.1.1 for full compatibility with Lagom 1.4.0.

You can find more information in the [`conductr-lib` README file](https://github.com/typesafehub/conductr-lib/blob/master/README.md).

Edit the `project/plugins.sbt` file to update `sbt-conductr` to version 2.5.1 or later:

```scala
addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % "2.5.1")
```

This automatically includes the correct version of `conductr-lib`.
