# Lagom component technologies

As a complete microservices platform, Lagom assembles a collection of technologies and adds value on top of them.

Some of these libraries and tools were developed at Lightbend, others are third-party and open-source.

## Architectural concepts

### Immutability

Lagom makes heavy use of immutable values, for example to represent commands, events, and states.  We recommend using the third-party [Immutables](https://immutables.github.io) library to define immutable objects in Java.

For immutable collections, we recommend [PCollections](http://pcollections.org), but you may also use another library such as [Guava collections](https://github.com/google/guava/wiki/ImmutableCollectionsExplained).

In this manual, see [[Immutable Objects|Immutable]] for more details.

### Event sourcing

Lagom services that persist data are encouraged, but not required, to use an event-sourced architecture.  In this manual, see [[Event Sourcing and CQRS|ES_CQRS]] for a high-level explanation of what event sourcing is and why it is valuable and [[Persistent Entity|PersistentEntity]] for an introduction to Lagom's implementation of event sourcing.

## Java and Scala

Lagom's Java APIs target Java 8. They assume familiarity with Java 8 features such as [lambdas](http://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html), [default methods](http://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html), and [`Optional`](http://docs.oracle.com/javase/8/docs/api/java/util/Optional.html).

Most of Lagom is implemented in Scala.  (This is an implementation detail that doesn't normally concern Java programmers using Lagom APIs.)

## Serialization

Lagom's recommended serialization format is JSON.  The default engine used for JSON serialization and deserialization is [Jackson](https://github.com/FasterXML/jackson).

Other serialization formats are also supported.

## Cassandra

By default, Lagom services needing to persist data use [Cassandra](http://cassandra.apache.org) as database. For convenience, the development environment embeds a Cassandra server.  In this manual, [[Cassandra Server|CassandraServer]] describes Lagom's Cassandra support further.

Lagom services are also free to use an existing Cassandra database, or another database solution entirely.

## Play Framework

Lagom is implemented on top of [Play Framework](https://www.playframework.com), Lightbend's web framework.  This is an implementation detail that will not directly concern simple microservices.  More advanced users may wish to use some Play APIs directly.

Play is in turn built on the popular, standard [netty](http://netty.io) network transport library.

If you have an existing Play Framework application that you want to add microservices to, we provide support both in sbt and Maven to help you.

## Guice

Like Play, Lagom uses [Guice](https://github.com/google/guice) for dependency injection.

## Akka

Lagom [[Persistence|PersistentEntity]], [[Publish-Subscribe|PubSub]], and [[Cluster|Cluster]] are implemented on top of [Akka](http://akka.io/), Lightbend's toolkit for building concurrent, distributed, and resilient message-driven applications. This is an implementation detail that will not directly concern simple microservices. More advanced users may wish to use some Akka APIs [[directly|Akka]].

## Akka Streams

A Lagom service may be "simple" or "streamed"; this is described further under [[Implementing services|ServiceImplementation]].  Streaming, asynchronous Lagom services are built on top of [Akka Streams](http://doc.akka.io/docs/akka/2.4/java/stream/index.html).

Communication with browser-based clients is via [WebSockets](https://tools.ietf.org/html/rfc6455).

## Clustering

If you want to scale your microservices out across multiple servers, Lagom provides clustering via [Akka Cluster](http://doc.akka.io/docs/akka/2.4/java/cluster-usage.html).

Customers of Lightbend's [Reactive Platform](https://www.lightbend.com/products/lightbend-reactive-platform) can additionally use [[Lightbend ConductR|ConductR]] and Akka's [Split Brain Resolver](http://doc.akka.io/docs/akka/akka-commercial-addons-1.0/java/split-brain-resolver.html) for deployment and cluster management and [Lightbend Monitoring](https://www.lightbend.com/products/monitoring) for monitoring production systems.

See [[Cluster]] in this manual for more details.

## Configuration

Lagom and many of its component technologies are configured using the [Typesafe Config](https://github.com/typesafehub/config) library.  The configuration file format is [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md), a powerful and expressive superset of JSON.

## Logging

Lagom uses [SLF4J](http://www.slf4j.org/) for logging, backed by [Logback](http://logback.qos.ch/) as its default logging engine.
See the [[Logging]] section of this manual for more information.
