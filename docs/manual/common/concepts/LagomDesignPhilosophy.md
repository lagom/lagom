<!--- Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com> -->
# Lagom design philosophy

Consider some of the basic requirements of a Reactive Microservice as identified by Jonas Bonér (quotes extracted from [*Reactive Microservices Architecture: Design Principles for Distributed Systems*] (https://info.lightbend.com/COLL-20XX-Reactive-Microservices-Architecture-RES-LP.html)):

* "*Isolation* is a prerequisite for resilience and elasticity and requires asynchronous communication between service boundaries ..."
* "An *autonomous service* can only *promise* its own behaviour by publishing its protocol/API." and "For a service to become location transparent, it needs to be addressable."
* "What is needed is that each Microservice take sole responsibility for their own state and the persistence thereof."

The following Lagom characteristics promote these best practices:

* Lagom is asynchronous by default --- its APIs make inter-service communication via streaming a first-class concept. All Lagom APIs use the asynchronous IO capabilities of [Akka Stream](https://akka.io/) for asynchronous streaming; the Java API uses [JDK8 `CompletionStage`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html)  for asynchronous computation; the Scala API uses [Futures](https://www.scala-lang.org/files/archive/api/2.12.x/scala/concurrent/Future.html).

* Lagom favors distributed persistent patterns in contrast with traditional centralized databases. We encourage --- but do not require --- an event-sourced architecture for data persistence. The default pattern for persisting entities takes advantage of Event Sourcing (ES) with Command Query Responsibility Segregation (CQRS). [[Managing data persistence|ES_CQRS]] explains at a high level what event sourcing is and why it is valuable. [[Persistent Entity|PersistentEntity]] introduces Lagom's implementation of event sourcing.

* Lagom provides an implementation of a service registry and a service gateway for development purposes along with the internal plumbing for managing client and server-side service discovery. [[Registering and discovering services|ServiceDiscovery]] introduces these concepts.

