# Getting started with Lagom in Maven

This page shows how to create and run your first Lagom project.

## Creating a new Lagom project

A Lagom system is typically made up of a set of maven projects, each build providing multiple services.  The easiest way to get started with a new Lagom system is to create a new project using the Maven archetype plugin, with the Lagom Maven archetype:

```
$ mvn archetype:generate -DarchetypeGroupId=com.lightbend.lagom \
  -DarchetypeArtifactId=maven-archetype-lagom-java -DarchetypeVersion=1.1.0
```

> **Note:** Ensure you replace the 1.1.0 archetype version with the latest version of Lagom.

This will prompt you for a `groupId`, `artifactId` and `version`.  You might choose, for example, `com.example` as a `groupId`, and `my-first-system` for an `artifactId`.  Once you've followed the prompts, it will create a new system with two services in it: `hello` and `stream`.

## Anatomy of a Lagom project

The created project contains the following elements:

```
my-first-system          → Project root
 └ hello-api             → hello world api project
 └ hello-impl            → hello world implementation project
 └ stream-api            → stream api project
 └ stream-impl           → stream implementation project
 └ integration-tests     → Integration tests
 └ pom.xml               → Project root build file
```

Notice how each service is broken up into two projects: api and implementation. The api project contains a service interface through which consumers may interact with the service. While the implementation project contains the actual service implementation.

## Understanding services projects


* The service interface is always placed in the api project. For instance, the service interface for the `hello` service can be found in the `hello-api` project (look for the `HelloService.java` source file).

@[helloservice-interface](code/docs/javadsl/gettingstarted/helloservice/HelloService.java)

* The service interface needs to inherit from [`Service`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html) and provide an implementation of [`Service.descriptor`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html#descriptor--) method.

* The implementation of `Service.descriptor` returns a [`Descriptor`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.html). A `Descriptor` defines the service name and the REST endpoints offered by a service. For each declared endpoint, an abstract method is declared in the service interface, e.g., see the `HelloService.hello` method.

* The implementation of the service abstract methods is provided by the related implementation project. For instance, the service implementation of the `HelloService.hello` method, for the `hello` service, can be found in the `hello-impl` project (look for the `HelloServiceImpl.java` source file).

@[helloservice-impl](code/docs/javadsl/gettingstarted/helloservice/HelloServiceImpl.java)

* The [`PersistentEntityRegistry`](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntityRegistry.html) allows to persist data in the database using [[Event Sourcing and CQRS|ES_CQRS]].

## Running Lagom services

Lagom includes a development environment that let you start all your services by invoking the `runAll` task on the Lagom maven plugin. Open the terminal and `cd` to your Lagom project (some log output cut for brevity):

```console
$ cd my-first-system
$ mvn lagom:runAll
...
[info] Starting embedded Cassandra server
..........
[info] Cassandra server running at 127.0.0.1:4000
[info] Service locator is running at http://localhost:8000
[info] Service gateway is running at http://localhost:9000
...
[info] Service hello-impl listening for HTTP on 0:0:0:0:0:0:0:0:24266
[info] Service stream-impl listening for HTTP on 0:0:0:0:0:0:0:0:26230
(Services started, press enter to stop and go back to the console...)
```

You can verify that the services are indeed up and running by invoking one of its endpoints from any HTTP client, such as a browser. The following request returns the message `Hello, World!`:

```
http://localhost:9000/api/hello/World
```

If you are wondering why we have created two services in the seed template, instead of having just one, the reason is simply that ([quoting](https://twitter.com/jboner/status/699536472442011648) Jonas Bonér):

> One microservice is no microservice - they come in systems.

Said otherwise, we believe you will be creating several services, and we felt it was important to showcase intra-service communication.

When developing in Lagom you will run several services in a single Java Virtual Machine. You may need to [[Increase Memory for Maven|JVMMemoryOnDev]].
