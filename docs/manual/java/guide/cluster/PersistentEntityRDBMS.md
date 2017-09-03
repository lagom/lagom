# Storing Persistent Entities in a Relational Database

This page describes how to configure a relational database for use with Lagom's [[Persistent Entity|PersistentEntity]] API.

## Project dependencies

To use a relational database add the following in your project's build:

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-persistence-jdbc_2.11</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[jdbc-dependency](code/build-cluster.sbt)

You will also need to add the jar for your JDBC database driver.

## Configuration

Lagom uses the [`akka-persistence-jdbc`](https://github.com/dnvriend/akka-persistence-jdbc) plugin to persist entities to the database.  This supports four different relational databases:

* [PostgreSQL](https://www.postgresql.org/)
* [MySQL](https://www.mysql.com/)
* [Oracle](https://www.oracle.com/database/index.html)
* [H2](https://www.h2database.com/)

We advise against using H2 in production, however, it is suitable for use in development and testing.

In Lagom's default configuration, Lagom will use Play's JDBC support to configure and create a connection pool. Details on how to configure it can be found [here](https://www.playframework.com/documentation/2.6.x/JavaDatabase). Play should be configured to provide a JNDI binding for the datasource, by default Lagom binds it to `DefaultDS`.

Lagom then configures `akka-persistence-jdbc` to use that `DefaultDS` JNDI binding. `akka-persistence-jdbc` uses [Slick](http://slick.lightbend.com/) to map tables and manage asynchronous execution of JDBC calls. This means we need to configure it to use the right Slick profile for your database, by default Lagom will use the H2 profile.

So for example, to configure a PostgreSQL database, you can add the following to your `application.conf`:

```
db.default {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://database.example.com/playdb"
}

jdbc-defaults.slick.profile = "slick.jdbc.PostgresProfile$"
```

## Table creation

By default, Lagom will automatically create the tables it needs for you if they don't already exist.  This is great for development and testing, but in some circumstances may not be appropriate for production.  The table auto creation feature can be disabled by using the following configuration:

```
lagom.persistence.jdbc.create-tables.auto = false
```

The database schemas needed for the tables can be found [here](https://github.com/dnvriend/akka-persistence-jdbc/tree/v2.6.8/src/test/resources/schema).

The full configuration options that Lagom provides for managing the creation of tables is here:

@[persistence](../../../../../persistence-jdbc/core/src/main/resources/reference.conf)
