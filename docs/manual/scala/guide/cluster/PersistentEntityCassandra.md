# Storing Persistent Entities in Cassandra

This page describes how to configure Cassandra for use with Lagom's [[Persistent Entity|PersistentEntity]] API.

## Project dependencies

To use Cassandra add the following in your project's build:

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-scaladsl-persistence-cassandra_2.11</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[cassandra-dependency](code/build-cluster.sbt)

## Configuration

Lagom's Cassandra support is provided by the [`akka-persistence-cassandra`](https://github.com/akka/akka-persistence-cassandra) plugin. A full configuration reference can be in the plugins [`reference.conf`](https://github.com/akka/akka-persistence-cassandra/blob/v0.20/src/main/resources/reference.conf).
