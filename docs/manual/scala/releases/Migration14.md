# Lagom 1.4 Migration Guide

This guide explains how to migrate from Lagom 1.3 to Lagom 1.4. If you are upgrading from an earlier version, be sure to review previous migration guides.

## Build changes

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.4.0-M3")
```

## Binding services

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

## Configuring Cassandra keyspaces

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

## Relational Databases - Akka Persistence JDBC

If you are using Lagom's `Persistent Entity` API with a relational database, you will need to add an index to your journal table.

The relational database support is based on `akka-persistence-jdbc` plugin. The plugin was updated to version 3.0.1, which include an important [bug fix](https://github.com/dnvriend/akka-persistence-jdbc/issues/96) that requires a new column index. Failing in updating your database schema will result in degraded performance when querying events.

Bellow you will find the index creation statement for each supported database.

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

Moreover, in `akka-persistence-jdbc` 3.0.x series, the `Events` query treats the offset as exclusive instead of inclusive. In general, this should not be a problem. Previous versions of Lagom had a workaround for it and this change in behavior should be transparent. This will only impact you if you were using the `Akka Persistence Query` directly.

## Upgrading to Play 2.6 and Akka 2.5

The internal upgrade to latest major versions of Play and Akka may need some changes in your code if you are using either of them directly. Please refer to the [Play 2.6 migration guide](https://www.playframework.com/documentation/2.6.x/Migration26) and the [Akka 2.5 migration guide](https://doc.akka.io/docs/akka/current/project/migration-guide-2.4.x-2.5.x.html?language=scala) for more details.

### Default Service Locator port

Historically, Lagom's service locator has listened on port 8000. Because port 8000 is a common port on which apps listen, its default value has been changed to 9008.

### Rolling upgrade

When running a rolling upgrade the nodes composing your Akka cluster must keep the ability to connect to each other and must use the same serialization formats.

One relevant change Akka 2.5 introduced involves a new method (DData) to [internally handle the sharding](https://doc.akka.io/docs/akka/current/project/migration-guide-2.4.x-2.5.x.html?language=scala#cluster-sharding-state-store-mode) of your Persistent Entities in Lagom. We have decided to not enable that new method so your migration from Lagom 1.3.x to 1.4.x should be fine. You may opt in and use DData instead of the default persistence-based one. Switching from persistence-based to DData requires a complete-cluster shutdown.

The Java serialization was already discouraged and since Lagom 1.4.0 it is not the default anymore. This is a setting we inherit from Akka and which we are propagating transparently. If your code was dependant on the Java serialization you will need to review your serializers. This change in the defaults will also affect your ability to do a rolling upgrade. If you must support rolling upgrades and you depended on the default serializations you may override the new defaults using the [additional-serialization-bindings](https://doc.akka.io/docs/akka/current/project/migration-guide-2.4.x-2.5.x.html?language=scala#additional-serialization-bindings) settings.

Lagom 1.4.x has switched to a new serialization format for one of its internal messages. This new format was added in Lagom 1.3.10, but not enabled. If you are doing a rolling upgrading from 1.3.10 or later to 1.4.x, then the change over will work with no problems. However, if you're doing a rolling upgrading from 1.3.9 or earlier to 1.4.x, then you will need to add the following configuration to your application to disable the new serializer until all nodes are upgraded to a version of Lagom that has the serializer:

```
akka.actor.serialization-bindings {
  "com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTaskActor$Execute$" = java
}
```

Once all nodes are upgraded to 1.4.x, you should then remove the above configuration for the next rolling upgrade. For more details on this process and why it's needed, see [here](https://github.com/lagom/lagom/issues/933#issuecomment-327738303).

## Breaking Changes

The return types of the method below were changed, which could result in deprecation warnings:

* Typesafe config is now used instead of Play config in `AdditionalConfiguration.configuration`, see [881](https://github.com/lagom/lagom/pull/881)
* The return types of `LagomServerBuilder.buildRouter`, `LagomServerBuilder.router`, and `LagomServer.router` were changed to be strongly typed from `Router` to `LagomServiceRouter` in [888](https://github.com/lagom/lagom/pull/888)
* `PlayJsonSerializer` serialization of Non-`JsObject`s was fixed in [1071](https://github.com/lagom/lagom/pull/1071), changing the return type of `JsonMigration.transfrorm` from `JsObject` to `JsValue`
