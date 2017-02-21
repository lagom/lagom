# Understanding Hello World

After creating and running Hello World from the command line, you no doubt appreciate what Lagom framework did for you. There was no need to determine what infrastructure you might need and then install and configure it. The template removed the necessity to set up a project or build structure. And, as you create services of your own, Lagom detects changes and performs a hot reload! Lagom allows you to concentrate on satisfying your business needs.

The separation of concerns illustrated in Hello World and an introduction to service descriptors and the registry will help you as you start developing your own microservices:

* [Service interface](#Service-interface)
* [Service implementation](#Service-implementation)

# Service interface

The service interface belongs in the `api` project. For instance, the service interface for the `hello` service resides in the `hello-api` project (look for the `HelloService.scala` source file).

@[helloservice](code/GettingStarted.scala)

Note that:

* The service interface inherits from [`Service`](api/com/lightbend/lagom/scaladsl/api/Service.html) and provides an implementation of [`Service.descriptor`](api/com/lightbend/lagom/scaladsl/api/Service.html#descriptor) method.

* The implementation of `Service.descriptor` returns a [`Descriptor`](api/com/lightbend/lagom/scaladsl/api/Descriptor.html). The `HelloService` descriptor defines the service name and the REST endpoints it offers. For each endpoint, declare an abstract method in the service interface as illustrated in the `HelloService.hello` method. For more information, see [[Service Descriptors|ServiceDescriptors]].

# Service implementation

The related `impl` project, `hello-impl` provides implementation for the service abstract methods. For instance, the `HelloServiceImpl.scala` source file contains the service implementation of the `HelloService.hello` method for the `hello` service. The [`PersistentEntityRegistry`](api/com/lightbend/lagom/scaladsl/persistence/PersistentEntityRegistry.html) supports data persistence using [[Event Sourcing and CQRS|ES_CQRS]].

@[helloserviceimpl](code/GettingStarted.scala)

