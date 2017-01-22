# Getting started with Lagom

This page shows how to create and run your first Lagom project using sbt.

## Creating a new Lagom project

A Lagom system is typically made up of a set of sbt builds, each build providing multiple services.  The easiest way to get started with a new Lagom system is to create a new project using the `lagom` sbt Giter8 template:

```
$ sbt new lagom/lagom-scala.g8
```

After following the prompts, this will create a new system with two services, if you went with the default name of `Hello`, these services will be called `hello` and `hello-stream`. The documentation below assumes that you selected the default name of `Hello`.

## Anatomy of a Lagom project

The created project contains the following elements:

```
hello                   → Project root
 └ hello-api            → hello api project
 └ hello-impl           → hello implementation project
 └ hello-stream-api     → hello-stream api project
 └ hello-stream-impl    → hello-stream implementation project
 └ project              → sbt configuration files
   └ build.properties   → Marker for sbt project
   └ plugins.sbt        → sbt plugins including the declaration for Lagom itself
 └ build.sbt            → Your project build file
```

* Notice how each service is broken up into two projects: api and implementation. The api project contains a service interface through which consumers may interact with the service. While the implementation project contains the actual service implementation.
* The `project` folder contains sbt specific files.
* The `build.sbt` file, which contains all information necessary to build, run, and deploy your services.   

## Understanding services projects

* The service interface is always placed in the api project. For instance, the service interface for the `hello` service can be found in the `hello-api` project (look for the `HelloService.scala` source file).

@[helloservice](code/GettingStarted.scala)

* The service interface needs to inherit from [`Service`](api/com/lightbend/lagom/scaladsl/api/Service.html) and provide an implementation of [`Service.descriptor`](api/com/lightbend/lagom/scaladsl/api/Service.html#descriptor) method.

* The implementation of `Service.descriptor` returns a [`Descriptor`](api/com/lightbend/lagom/scaladsl/api/Descriptor.html). A `Descriptor` defines the service name and the REST endpoints offered by a service. For each declared endpoint, an abstract method is declared in the service interface, e.g., see the `HelloService.hello` method.

* The implementation of the service abstract methods is provided by the related implementation project. For instance, the service implementation of the `HelloService.hello` method, for the `hello` service, can be found in the `hello-impl` project (look for the `HelloServiceImpl.scala` source file).

@[helloserviceimpl](code/GettingStarted.scala)

* The [`PersistentEntityRegistry`](api/com/lightbend/lagom/scaladsl/persistence/PersistentEntityRegistry.html) allows to persist data in the database using [[Event Sourcing and CQRS|ES_CQRS]].

## Running Lagom services

Lagom includes a development environment that let you start all your services by simply typing `runAll` in the sbt console. Open the terminal and `cd` to your Lagom project:

```console
$ cd hello
$ sbt
... (booting up)
> runAll
[info] Starting embedded Cassandra server
..........
[info] Cassandra server running at 127.0.0.1:4000
[info] Service locator is running at http://localhost:8000
[info] Service gateway is running at http://localhost:9000
[info] Service hello-impl listening for HTTP on 0:0:0:0:0:0:0:0:24266
[info] Service hello-stream-impl listening for HTTP on 0:0:0:0:0:0:0:0:26230
(Services started, press enter to stop and go back to the console...)
```

You can verify that the services are indeed up and running by invoking one of its endpoints from any HTTP client, such as a browser. The following request returns the message `Hello, World!`:

```
http://localhost:9000/api/hello/World
```


If you are wondering why we have created two services in the seed template, instead of having just one, the reason is simply that ([quoting](https://twitter.com/jboner/status/699536472442011648) Jonas Bonér):

> One microservice is no microservice - they come in systems.

Said otherwise, we believe you will be creating several services, and we felt it was important to showcase intra-service communication.

The `lagom-scala.g8` template you used to build `hello` uses the `.sbtopts` file to increase the memory used by the `JVM` when starting your project. There's a few more ways to [[Increase Memory in sbt|JVMMemoryOnDev]]

## Larger-scale sample project
The [online-auction-scala](https://github.com/lagom/online-auction-scala) is a larger project comprising a number of services. Downloading this project and inspecting the source code will provide a valuable supplemental resource for this documentation.
