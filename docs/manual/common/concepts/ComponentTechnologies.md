# Component technologies

As a complete microservices platform, Lagom assembles a collection of technologies and adds value on top of them. Some of the libraries, tools, and servers that Lagom uses and supports were developed at [Lightbend](https://lightbend.com), others are third-party and open-source. As you develop with Lagom, you can also take advantage of these technologies:

* Play Framework --- Lagom is implemented on top of [Play Framework](https://www.playframework.com), Lightbend's web framework. This is an implementation detail that does not directly concern you when developing simple microservices.  However, advanced users can call some Play APIs directly. If you have an existing Play Framework application to which you want to add microservices, Lagom provides support for that use case.

* Akka --- Lagom [[Persistence|PersistentEntity]], [[Publish-Subscribe|PubSub]], and [[Cluster|Cluster]] are implemented on top of [Akka](https://akka.io/), Lightbend's toolkit for building concurrent, distributed, and resilient message-driven applications. (This is an implementation detail that does not directly concern you when developing simple microservices. However, you can call also [[Akka APIs|Akka]] directly.)

    * To scale your microservices out across multiple servers, Lagom provides clustering via [Akka Cluster](https://doc.akka.io/docs/akka/2.6/cluster-usage.html).

    * As described in [[Implementing services|ServiceImplementation]], A Lagom service may be "simple" or "streamed".  Streaming, asynchronous Lagom services are built on top of [Akka Streams](https://doc.akka.io/docs/akka/2.6/stream/index.html).

* [Lightbend Platform](https://www.lightbend.com/lightbend-platform) subscribers can use additional components to operationalize and production-harden their systems.

    * Akka [Split Brain Resolver](https://doc.akka.io/docs/akka-enhancements/current/split-brain-resolver.html) handles network failures and system crashes.

    * [Lightbend Telemetry](https://developer.lightbend.com/docs/telemetry/current/home.html) and [Lightbend Console](https://developer.lightbend.com/docs/console/current/) give you visibility into system health, availability and performance.

    * See [[Lightbend Platform|LightbendPlatform]] for more details.

* Cassandra --- By default, Lagom microservices that need to persist data use the  [Cassandra](https://cassandra.apache.org) instance that runs as part of the development environment. You can also use an existing [[Cassandra Server|CassandraServer]] database or another type of database. See [[Managing data persistence|ES_CQRS]] for more information.

* Guice --- Like Play, Lagom uses [Guice](https://github.com/google/guice) for dependency injection.

* SLF4J & Logback --- Lagom uses [SLF4J](https://www.slf4j.org/) for logging, backed by [Logback](https://logback.qos.ch/) as its default logging engine. See [[Logging]] for more information.

* Typesafe Config Library --- Lagom and many of its component technologies are configured using the [Typesafe Config](https://github.com/typesafehub/config) library.  The configuration file format is [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md), a powerful and expressive superset of JSON.

* Serialization --- Lagom's recommended serialization format is JSON.  The default engine used for JSON serialization and deserialization used for Java is [Jackson](https://github.com/FasterXML/jackson) and for Scala, [Play JSON](https://www.playframework.com/documentation/2.6.x/ScalaJson). Other serialization formats are also supported.

