# Service Discovery

Microservices must support location transparency: the ability to reside on multiple hosts for load balancing and resilience, to move from one host to another when failures occur, and to scale the number of hosts up and down as load changes. 

If a microservice's instances keep moving, spawning and dying, a mechanism for the microservice client to locate an instance is required. A Service Registry maintains an up-to-date list of available microservices that includes the host and port for each instance of each microservice. As load changes, the system can spawn or destroy instances in any location and continue to satisfy requests because the Service Registry collaborates with the microservice's instances to maintain the lookup table updated.

In order to register every microservice in the Service Registry there are two alternatives: self-registration and [3rd Party Registration](http://microservices.io/patterns/3rd-party-registration.html). When booting a Lagom microservice instance a registrar will register the name of the microservice, the URL and the names of the locatable Service Descriptors on the Service Registry so that they can be located. When powering down an instance of a service, the registrar will have to update the Service Registry too. Lagom's [[Developer Environment|DevEnvironment]] provides an implementation of a Service Registry and a registrar so you can run your microservices locally.

There are plenty of technologies providing Service Registry capabilities. You will need to choose and/or develop a Service Locator for your services to run in your deployment environment (see for example [Lagom ZooKeeper Service Locator](https://github.com/jboner/lagom-service-locator-zookeeper)). You may need to work out a way to plug your Lagom services with a registrar. Lagom's integration with [[ConductR|ConductR]] makes this two steps seamless.


## Client-Side Service Discovery

From Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](http://www.oreilly.com/programming/free/reactive-microservices-architecture.html) 

> Once the information about each service has been stored it can be made available through a Service Registry that services can use to look the information up—using a pattern called Client-Side Service Discovery.

Lagom creates service clients for each Service Descriptor so that applications can interact with Lagom Services. So [[an app|IntegratingNonLagom]] that wanted to consume the hello service can use the Welcome Service Client and simply invoke the `hello` method. The Welcome Service Client will use the Service Registry to find a valid URL where  `welcome` is available and with that information the request is then fulfilled. This approach requires using Lagom provided code.

When running your code in production the Service Locator plugged into your services will be an element participating in this Client-Side Discovery.

## Server-Side Service Discovery

From Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](http://www.oreilly.com/programming/free/reactive-microservices-architecture.html) 

> Another strategy is to have the information stored and maintained in a load balancer [...] using a pattern called Server-Side Service Discovery.

Lagom Developer Mode starts all your services plus a Service Registry and a Service Gateway. The Service Gateway allows Service Clients to consume the endpoints provided by the Service Descriptors registered. A browser can display a hello message to a user by requesting the `/hello/steve` path to the Service Gateway. The Service Gateway will know what service provide that and will request the Service Registry for an instance of that service. The Service Registry will respond with the host and port of the instance where the request can be fulfilled. Finally the Service Gateway will perform the request and return the result to the browser. In this model, the browser only needs to know where the Service Gateway is. When using server-side discovery, a call on a Service Descriptor can only be reached if its call is added to the ACL.

This section uses HTTP-REST for all the examples but the concepts apply to any kind of traffic be it HTTP, binary over tcp, etc...

Server-Side Service Discovery is very convenient when you can't embed your Service Locator on every client.

