# Storing Persistent Entities in Cassandra

This page describes how to configure Cassandra for use with Lagom's [[Persistent Entity|PersistentEntity]] API.

## Project dependencies

To use Cassandra add the following in your project's build:

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-scaladsl-persistence-cassandra_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[cassandra-dependency](code/build-cluster.sbt)

## Configuration

Lagom's persistence needs a few tables to store its data. These tables are stored in Cassandra keyspaces. A keyspace in Cassandra is a namespace that defines data replication on Cassandra nodes. Each service should use a unique keyspace name so that the tables of different services do not conflict with each other. You need to configure the keyspaces that are used for these tables in each of your service implementation projects.

Cassandra keyspace names must start with an alphanumeric character and contain only alphanumeric and underscore characters. They are case-insensitive and stored in lowercase.

Lagom has three internal components that require keyspace configuration:

* The **journal** stores serialized events
* The **snapshot store** stores snapshots of the state as an optimization for faster recovery (see [[Snapshots|PersistentEntity#Snapshots]] for details)
* The **offset store** is used for [[Cassandra Read-Side support|ReadSideCassandra]] to keep track of the most recent event handled by each read-side processor (detailed in [[Read-side design|ReadSide#Read-side-design]]).

You can configure these keyspace names in each service implementation project's `application.conf` file:

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

When your service starts up, Lagom creates these keyspaces by default if they are missing, and automatically creates its internal tables within them. If you prefer to manage the schema explicitly, you can disable automatic creation with these properties:

```conf
cassandra-journal {
  keyspace-autocreate = false
  tables-autocreate = false
}
cassandra-snapshot-store {
  keyspace-autocreate = false
  tables-autocreate = false
}
lagom.persistence.read-side.cassandra {
  keyspace-autocreate = false
}
```

With these properties set to `false`, if the keyspaces or tables are missing at startup your service will log an error and fail to start.

Lagom's Cassandra support is provided by the [`akka-persistence-cassandra`](https://doc.akka.io/docs/akka-persistence-cassandra/0.99/) plugin. A full configuration reference is available in the plugin's [`reference.conf`](https://github.com/akka/akka-persistence-cassandra/blob/v0.99/core/src/main/resources/reference.conf).

## Cassandra Location

Lagom will start an embedded Cassandra server when running in developer mode. You can review the configuration options or how to disable the embedded server in the section on Cassandra Server in [[Running Lagom in development|CassandraServer]].

In production you usually will prefer a dynamically locatable Cassandra server for resiliency. If you need to use a static list of contact-points to locate your Cassandra server review the section on deploying using static service location for Cassandra Service in [[Running Lagom in Production|ProductionOverview#Using-static-Cassandra-contact-points]].
