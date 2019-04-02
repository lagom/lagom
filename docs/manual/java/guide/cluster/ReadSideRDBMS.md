# Relational Database Read-Side support

This page is specifically about Lagom's support for relational database read-sides.  Before reading this, you should familiarize yourself with Lagom's general [[read-side support|ReadSide]].

Lagom supports two options for accessing relational databases in read-sides: using the JDBC API directly, or using the [Java Persistence API (JPA)](https://www.oracle.com/technetwork/java/javaee/tech/persistence-jsp-140049.html) to automatically map between Java objects and relational data.

## Choosing between JDBC and JPA

JDBC offers a very low-level API, directly mapping to the capabilities of most database drivers. The queries you supply are passed directly to the back-end database, so they may require using implementation-specific dialects of SQL. Query results are returned as a [ResultSet](https://docs.oracle.com/javase/7/docs/api/java/sql/ResultSet.html), which allows you to iterate through the rows of the result and retrieve column data.

JPA builds on top of JDBC to provide object-relational mapping capabilities. Amongst many other features, JPA includes the ability to generate database schemas from Java classes, to retrieve, insert and update rows by mapping Java object properties to database columns, and to write queries in a database-independent, SQL-like query language or type-safe criteria API.

Using JPA with a Lagom service requires adding an additional dependency from your project to a JPA 2.1 provider such as [Hibernate ORM](http://hibernate.org/orm/) (recommended) or [EclipseLink](https://www.eclipse.org/eclipselink/).

Both options build on top of the same support for [[storing persistent entities in a relational database|PersistentEntityRDBMS]] and share the same offset store. It is possible to mix both JDBC-based and JPA-based read-sides in the same service.

## Implementing a relational database read side

Follow the instructions for the specific relational database API you want to use:
