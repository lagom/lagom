# Lagom component technologies

As a complete microservices platform, Lagom assembles a collection of technologies and adds value on top of them.

Some of these libraries and tools were developed at Lightbend, others are third-party and open-source.

## Architectural concepts

### Immutability

Lagom makes heavy use of immutable values, for example to represent commands, events, and states.  We recommend using the third-party [Immutables](https://immutables.github.io) library to define immutable objects in Java.

For immutable collections, we recommend PCollections, but you may also use another library such as Guava collections.

In this manual, see [[Immutable Objects|Immutable]] for more details.

### Event sourcing

Lagom services that persist data are encouraged, but not required, to use an event-sourced architecture.  In this manual, see [[Event Sourcing and CQRS|ES_CQRS]] for a high-level explanation of what event sourcing is and why it is valuable and [[Persistent Entity|PersistentEntity]] for an introduction to Lagom's implementation of event sourcing.

## Java and Scala

The first version of Lagom provides a Java API for writing microservices.  Before long, a subsequent version will add a Scala API.

Lagom's Java APIs target Java 8. They assume familiarity with Java 8 features such as lambdas, default methods, and `Optional`.

Most of Lagom is implemented in Scala.  (This is an implementation detail that doesn't normally concern Java programmers using Lagom APIs.)

## sbt build tool

One place where some light Scala coding is required, even for Java users, is in Lagom build definitions, which must be written using sbt's Scala DSL.  In this manual, [[Build Concepts|BuildConcepts]] explains why we use and require sbt.

## Serialization

Lagom's recommended serialization format is JSON.  The default engine used for JSON serialization and deserialization is [Jackson](https://github.com/FasterXML/jackson).

Other serialization formats are also supported.

## Cassandra

By default, Lagom services needing to persist data use Cassandra as database. For conveniency, the development environment embeds a Cassandra server.  In this manual, [[Cassandra Server|CassandraServer]] describes Lagom's Cassandra support further.

Lagom services are also free to use an existing Cassandra database, or another database solution entirely.

## Play framework

Lagom is implemented on top of Play Framework, Lightbend's web framework.  This is an implementation detail that will not directly concern simple microservices.  More advanced users may wish to use some Play APIs directly.

Play is in turn built on the popular, standard [netty](http://netty.io) network transport library.

If you have an existing Play Framework application that you want to add microservices to, we provide a sbt plugin (PlayLagom) to help you.

## Guice

Like Play, Lagom uses Guice for dependency injection.

## Akka

Lagom [[Persistence|PersistentEntity]], [[Publish-Subscribe|PubSub]], and [[Cluster|Cluster]] are implemented on top of Akka, Lightbend's toolkit for building concurrent, distributed, and resilient message-driven applications. This is an implementation detail that will not directly concern simple microservices. More advanced users may wish to use some Akka APIs [[directly|Akka]].

## Akka Streams

A Lagom service may be "simple" or "streamed"; this is described further under [[Implementing services|ServiceImplementation]].  Streaming, asynchronous Lagom services are built on top of [Akka Streams](http://doc.akka.io/docs/akka/2.4.2/java/stream/index.html).

Communication with browser-based clients is via WebSockets.

## Clustering

If you want to scale your microservices out across multiple servers, Lagom provides clustering via [Akka Cluster](http://doc.akka.io/docs/akka/2.4.2/java/cluster-usage.html).

Customers of Lightbend's [Reactive Platform](http://www.lightbend.com/products/lightbend-reactive-platform) can additionally use [[Lightbend ConductR|ConductR]] and Akka's [Split Brain Resolver](http://doc.akka.io/docs/akka/rp-16s01p03/scala/split-brain-resolver.html) for deployment and cluster management and [Lightbend Monitoring](http://www.lightbend.com/products/monitoring) for monitoring production systems.

See [[Cluster]] in this manual for more details.

## Configuration

Lagom and many of its component technologies are configured using the [Typesafe Config](https://github.com/typesafehub/config) library.  The configuration file format is [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md), a powerful and expressive superset of JSON.

## Logging

Lagom uses SLF4J for logging, backed by [Logback](http://logback.qos.ch/) as its default logging engine.
See the [[Logging]] section of this manual for more information.
