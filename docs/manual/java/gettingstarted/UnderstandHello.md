# Understanding Hello World

After creating and running Hello World from the command line, you no doubt appreciate what Lagom framework did for you. There was no need to determine what infrastructure you might need and then install and configure it. The template removed the necessity to set up a project or build structure. And, as you create services of your own, Lagom detects changes and performs a hot reload! Lagom allows you to concentrate on satisfying your business needs.

The separation of concerns illustrated in Hello World and an introduction to service descriptors and the registry will help you as you start developing your own microservices:

* [Service interface](#Service-interface)
* [Service implementation](#Service-implementation)

## Service interface
The source file defining a service interface belongs in the service's api project.  For example, in Hello World, `HelloService.java`, the source file for the `hello` service interface resides in the `hello-api` directory of the Maven project or sbt build.

@[helloservice-interface](code/docs/javadsl/gettingstarted/helloservice/HelloService.java)

Note that:
 
* The service interface inherits from [`Service`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html) and provides an implementation of [`Service.descriptor`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html#descriptor--) method.

* The implementation of `Service.descriptor` returns a [`Descriptor`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.html). The `HelloService` descriptor defines the service name and the REST endpoints it offers. For each endpoint, declare an abstract method in the service interface, as illustrated in the `HelloService.hello` method. For more information, see [[Service Descriptors|ServiceDescriptors]].

## Service implementation

The related `impl` directory contains the implementation of the service interface's abstract methods. For instance, `HelloServiceImpl.java` in the `hello-impl` directory implements the `hello` service `HelloService.hello` method. It includes the  [`PersistentEntityRegistry`](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntityRegistry.html), which allows you to persist data in the database using [[Event Sourcing and CQRS|ES_CQRS]]. 

@[helloservice-impl](code/docs/javadsl/gettingstarted/helloservice/HelloServiceImpl.java)



