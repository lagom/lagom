# Getting started with Lagom in sbt

This page shows how to create and run your first Lagom project using sbt.

## Creating a new Lagom project

A Lagom system is typically made up of a set of sbt builds, each build providing multiple services.  The easiest way to get started with a new Lagom system is to create a new project using the `lagom` Activator template:

```
$ activator new my-first-system lagom-java
```

This will create a new system with two services in it: `helloworld` and `hellostream`.

## Anatomy of a Lagom project

The created project contains the following elements:

```
my-first-system          → Project root
 └ hellostream-api       → hellostream api project
 └ hellostream-impl      → hellostream implementation project
 └ helloworld-api        → helloworld api project
 └ helloworld-impl       → helloworld implementation project
 └ project               → sbt configuration files
   └ build.properties    → Marker for sbt project
   └ plugins.sbt         → sbt plugins including the declaration for Lagom itself
 └ build.sbt             → Your project build file
```

* Notice how each service is broken up into two projects: api and implementation. The api project contains a service interface through which consumers may interact with the service. While the implementation project contains the actual service implementation.
* The `project` folder contains sbt specific files.
* The `build.sbt` file, which contains all information necessary to build, run, and deploy your services.   

## Understanding services projects

* The service interface is always placed in the api project. For instance, the service interface for the `helloworld` service can be found in the `helloworld-api` project (look for the `HelloService.java` source file).

@[helloservice-interface](code/sample/helloworld/api/HelloService.java)

* The service interface needs to inherit from [`Service`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html) and provide an implementation of [`Service#descriptor`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html#descriptor--) method.

* The implementation of `Service#descriptor` returns a [`Descriptor`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.html). A `Descriptor` defines the service name and the REST endpoints offered by a service. For each declared endpoint, an abstract method is declared in the service interface, e.g., see the `HelloService#hello` method.

* The implementation of the service abstract methods is provided by the related implementation project. For instance, the service implementation of the `HelloService#hello` method, for the `helloworld` service, can be found in the `helloworld-impl` project (look for the `HelloServiceImpl.java` source file).

@[helloservice-impl](code/sample/helloworld/impl/HelloServiceImpl.java)

* The [`PersistentEntityRegistry`](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntityRegistry.html) allows to persist data in the database using [[Event Sourcing and CQRS|ES_CQRS]].

## Running Lagom services

Lagom includes a development environment that let you start all your services by simply typing `runAll` in the activator console. Open the terminal and `cd` to your Lagom project:

```console
$ cd my-first-system
$ activator
... (booting up)
> runAll
[info] Starting embedded Cassandra server
..........
[info] Cassandra server running at 127.0.0.1:4000
[info] Service locator is running at http://localhost:8000
[info] Service gateway is running at http://localhost:9000
[info] Service helloworld-impl listening for HTTP on 0:0:0:0:0:0:0:0:24266
[info] Service hellostream-impl listening for HTTP on 0:0:0:0:0:0:0:0:26230
(Services started, use Ctrl+D to stop and go back to the console...)
```

You can verify that the services are indeed up and running by exercising one of its endpoints, e.g:

```console
$ curl http://localhost:9000/api/hello/World
```

And you should get back the message `Hello, World!`.

If you are wondering why we have created two services in the seed template, instead of having just one, the reason is simply that ([quoting](https://twitter.com/jboner/status/699536472442011648) Jonas Bonér):

> One microservice is no microservice - they come in systems.

Said otherwise, we believe you will be creating several services, and we felt it was important to showcase intra-service communication.
