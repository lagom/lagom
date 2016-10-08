<!--- Copyright (C) 2016 Lightbend Inc. <https://www.lightbend.com> -->
# Lagom design philosophy

Lagom's design rests on these main principles:

## Asynchronous

Lagom is asynchronous by default.

All Lagom APIs use the asynchronous IO capabilities of [Akka Stream](http://akka.io/) for asynchronous streaming and the [JDK8 `CompletionStage`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) API for asynchronous computation.

Furthermore, Lagom also makes asynchronous communication the default: when communicating between services, streaming is provided as a first-class concept. Developers are encouraged and enabled to use asynchronous messaging via streaming, rather than synchronous request-response communication. Asynchronous messaging is fundamental to system resilience and scalability.

## Distributed persistence

Lagom favours distributed persistent patterns, in contrast to the traditional centralized database. [[Event Sourcing (ES) with Command Query Responsibility Segregation (CQRS)|ES_CQRS]] is the default way to persist entities in Lagom.

## Developer productivity

Lagom places a high emphasis on productivity. Developers should be focused on solving their business problems, not on wiring services together.

Lagom's [[expressive service interface declarations|ServiceDescriptors]] let developers quickly define interfaces and immediately start implementing them.

Lagom's development environment is particularly important in developer productivity, both while developing and in the maintenance of it. In a system with many services, developers should not be spending time updating their own environment to ensure that services are configured to run correctly. Going to production, developers should be able to just as easily run their services as they are doing during development.

Through using [[Lightbend ConductR|ConductR]] tooling, developers can first test the production configuration locally. Then, with zero friction, they push their services out to production and manage them through Lightbend ConductR interfaces.
