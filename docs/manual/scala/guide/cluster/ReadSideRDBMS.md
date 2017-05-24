# Relational Database Read-Side support

This page is specifically about Lagom's support for relational database read-sides.  Before reading this, you should familiarize yourself with Lagom's general [[read-side support|ReadSide]].

Lagom supports two options for accessing relational databases in read-sides: using the JDBC API directly, or using the [Slick](http://slick.lightbend.com/).

## Choosing between JDBC and Slick

JDBC offers a very low-level API, directly mapping to the capabilities of most database drivers. The queries you supply are passed directly to the back-end database, so they may require using implementation-specific dialects of SQL. Query results are returned as a [ResultSet](https://docs.oracle.com/javase/7/docs/api/java/sql/ResultSet.html), which allows you to iterate through the rows of the result and retrieve column data.

Slick is a modern database query and access library for Scala. It allows you to work with stored data almost as if you were using Scala collections while at the same time giving you full control over when a database access happens and which data is transferred. You can write your database queries in Scala instead of SQL, thus profiting from the static checking, compile-time safety and compositionality of Scala. Slick features an extensible query compiler which can generate code for different backends.

Using Slick with a Lagom service does not require any additional dependency.

Both options build on top of the same support for [[storing persistent entities in a relational database|PersistentEntityRDBMS]] and share the same offset store. It is possible to mix both JDBC-based and Slick-based read-sides in the same service.

## Implementing a relational database read side

Follow the instructions for the specific relational database API you want to use:
