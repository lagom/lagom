# API Overview

Lagom provides both Java and Scala APIs. The Java API targets Java 8 and assumes familiarity with features such as [lambdas](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html), [default methods](https://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html), and [`Optional`](https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html). Most of Lagom is implemented in Scala.  But, this is an implementation detail that shouldn't concern you, even if you are developing with the Java API.

Lagom's [[expressive service interface declarations|ServiceDescriptors]] let developers quickly define interfaces and immediately start implementing them.

Important APIs that you will use when developing with Lagom include:
* [[Service API|ServiceDescriptors]] --- Provides a way to declare and implement service interfaces, to be consumed by clients. For location transparency, clients discover services through a Service Locator. The Service API supports asynchronous streaming between services in addition to synchronous request-response calls.
* [[Message Broker API|MessageBrokerApi]] --- Provides a distributed publish-subscribe model that services can use to share data via topics. A topic is simply a channel that allows services to push and pull data. 
* [[Persistence API|PersistentEntity]] --- Provides event-sourced persistent entities for services that store data. Command Query Responsibility Segregation (CQRS) read-side support is provided as well. Lagom manages the distribution of persisted entities across a cluster of nodes, enabling sharding and horizontal scaling. Cassandra is provided as a database out-of-the-box, but other DBs are supported.




