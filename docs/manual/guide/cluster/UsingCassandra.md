# Using Cassandra

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

Lagom's Cassandra support