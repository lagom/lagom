# Storing Persistent Entities in Cassandra

This page describes how to configure Cassandra for use with Lagom's [[Persistent Entity|PersistentEntity]] API.

## Project dependencies

To use Cassandra add the following in your project's build:

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-persistence-cassandra_2.11</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[cassandra-dependency](code/build-cluster.sbt)

## Configuration

Lagom uses several internal Cassandra tables to persist entity data. These are known as the journal, which stores events, and the snapshot store, which stores snapshots of the state as an optimization for faster recovery (see [[Snapshots|PersistentEntity#Snapshots]] for details). [[Cassandra Read-Side support|ReadSideCassandra]] also uses a table to store the event offsets last processed by each read-side processor (detailed in [[Read-side design|ReadSide#Read-side-design]]). You will need to configure the keyspaces that contain the tables for each of these components.

A keyspace in Cassandra is a namespace that defines data replication on nodes. Each service should use a unique keyspace name so that the tables of different services do not conflict with each other.

Cassandra keyspace names must start with an alphanumeric character and contain only alphanumeric and underscore characters. They are case-insensitive and stored in lowercase.

You can configure these keyspaces in each service implementation project's `application.conf` file:

```conf
cassandra-journal.keyspace = users_journal
cassandra-snapshot-store.keyspace = users_snapshot
lagom.persistence.read-side.cassandra.keyspace = users_read_side
```

While different services should be isolated by using different keyspaces, it is perfectly fine to use the same keyspace for all of these components within one service. In that case, it can be convenient to define a custom keyspace configuration property and use [property substitution](https://github.com/typesafehub/config#factor-out-common-values) to avoid repeating it.

```conf
users.cassandra.keyspace = users

cassandra-journal.keyspace = ${users.cassandra.keyspace}
cassandra-snapshot-store.keyspace = ${users.cassandra.keyspace}
lagom.persistence.read-side.cassandra.keyspace = ${users.cassandra.keyspace}
```

By default, Lagom automatically creates the configured keyspaces and its internal tables if they are missing. If you prefer to manage the schema explicitly, you can disable automatic creation with these properties:

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

It is not possible to disable automatic creation of the offset store table at this time.

Lagom's Cassandra support is provided by the [`akka-persistence-cassandra`](https://github.com/akka/akka-persistence-cassandra) plugin. A full configuration reference can be in the plugin's [`reference.conf`](https://github.com/akka/akka-persistence-cassandra/blob/v0.20/src/main/resources/reference.conf).
