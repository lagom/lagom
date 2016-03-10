<!--- Copyright (C) 2016 Lightbend Inc. <https://www.lightbend.com> -->
# What is Lagom?

Lagom is a framework for creating microservice-based systems. It offers four main features:

* [[Service API|ServiceDescriptors]]
* [[Persistence API|PersistentEntity]]
* [[Development Environment|DevEnvironment]]
* [[Production Environment|ConductR]]

The [[Service API|ServiceDescriptors]] provides a way to declare and implement service interfaces, to be consumed by clients. For location transparency, clients discover services through a Service Locator. The Service API supports synchronous request-response calls as well as asynchronous streaming between services.

The [[Persistence API|PersistentEntity]] provides event-sourced persistent entities for services that store data. Command Query Responsibility Segregation (CQRS) read-side support is provided as well. Lagom manages the distribution of persisted entities across a cluster of nodes, enabling sharding and horizontal scaling. Cassandra is provided as a database out-of-the-box.

The [[Development Environment|DevEnvironment]] allows running all your services, and the supporting Lagom infrastructure, with one command. It hot-reloads your services when code changes; no fragile scripts are needed to set up and maintain a development environment. With Lagom, a developer can bring up a new service or join an existing Lagom development team in just a few minutes.

[[Lightbend ConductR|ConductR]] is the out-of-the-box supported production environment. Lightbend ConductR allows simple deployment, monitoring, and scaling, of Lagom services in a container environment.
