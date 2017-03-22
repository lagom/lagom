# API Overview

Lagom provides both Java and Scala APIs. The Java API targets Java 8 and assumes familiarity with features such as [lambdas](http://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html), [default methods](http://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html), and [`Optional`](http://docs.oracle.com/javase/8/docs/api/java/util/Optional.html). Most of Lagom is implemented in Scala.  But, this is an implementation detail that shouldn't concern you, even if you are developing with the Java API.

Lagom's [[expressive service interface declarations|ServiceDescriptors]] let developers quickly define interfaces and immediately start implementing them.

Important APIs that you will use when developing with Lagom include:
* [[Service API|ServiceDescriptors]]
    Provides a way to declare and implement service interfaces, to be consumed by clients. For location transparency, clients discover services through a Service Locator. The Service API supports asynchronous streaming between services in addition to synchronous request-response calls.
* [[Persistence API|PersistentEntity]]
    Provides event-sourced persistent entities for services that store data. Command Query Responsibility Segregation (CQRS) read-side support is provided as well. Lagom manages the distribution of persisted entities across a cluster of nodes, enabling sharding and horizontal scaling. Cassandra is provided as a database out-of-the-box, but other DBs are supported.

## Immutables
    
Lagom makes heavy use of [[immutable object values|Immutable]], for example to represent commands, events, and states. As you develop your microservices, we also recommend that you take advantage of the following:

* The third-party [Immutables](https://immutables.github.io) library to define immutable objects in Java.
* For immutable collections, [PCollections](http://pcollections.org), or other libraries such as [Guava collections](https://github.com/google/guava/wiki/ImmutableCollectionsExplained).

## Serialization

Lagom's recommended serialization format is JSON.  The default engine used for JSON serialization and deserialization is [Jackson](https://github.com/FasterXML/jackson).

Other serialization formats are also supported.