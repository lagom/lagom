# Service Discovery

Microservices must support scaling up and down and also must stay mobile. In those scenarios, a Service Registry (also referred to [[Service Locator|ServiceLocator]]) is required. A Service Registry contains an updated list of available microservices with a list of reachable `host:port`'s where each microservive can be found.

In order to register every microservice in the Service Registry there are two alternatives: self-registration and [3rd Party Registration](http://microservices.io/patterns/3rd-party-registration.html). When booting a Lagom microservice the registrator will register the name of the microservice, the URL and the names of the locatable Service Descriptors on the Service Locator so that they can be located. When powering down an instance of a service, the registrator will have to update the Service Registry too. Lagom's [[Developer Environment|DevEnvironment]] provides an implementation of a Service Registry and a registrator so you can run your microservices locally.

Lagom supports deploying many Service Descriptors inside a single microservice. That is not against the recommendation that a [[microservice should only do one thing|Microservices#does-this-service-do-only-one-thing-]] and do it well. An example of such setup is adding a metrics service or an admin service alongside your domain service. This way when you start an instance of your microservice your infrastructure and your admin tools will be able to query and tune the microservice. You could also model your domain in a way that single microservice contains multiple Service Descriptors. Following are the specifications of a `Greetings` microservice that contains two Service Descriptors (`welcome` and `farewell`) : 

 * A Microservice named "Greetings"
    * a Service Descriptor named "welcome" provides "/hello/:name"
    * a Service Descriptor named "farewell" provides "/goodbye/:name"

## Client-Side Service Discovery

From Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](http://www.oreilly.com/programming/free/reactive-microservices-architecture.html) 

> Once the information about each service has been stored it can be made available through a Service Locator that services can use to look the information up—using a pattern called Client-Side Service Discovery.

Lagom provides service clients for each Service Descriptor so that applications can interact with services using Client-Side service discovery. The lookup is made by descriptor name on the Service Registry. So an app that wanted to consume the hello service can use the Welcome Service Client and simply invoke the `hello` method. The Welcome Service Client is in charge or requesting the Service Locator what is a valid URL to locate `welcome` and with that information the request is then fulfilled. This approach requires using Lagom provided code.

## Server-Side Service Discovery

From Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](http://www.oreilly.com/programming/free/reactive-microservices-architecture.html) 

> Another strategy is to have the information stored and maintained in a load balancer [...] using a pattern called Server-Side Service Discovery.

Lagom provides a Service Gateway for the development environment so that client apps that don't use Lagom provided Service Clients can still consume the endpoints provided by the Service Descriptors registered. A browser can display a hello message to a user by requesting the `/hello/steve` path to the Service Gateway. The Service Gateway will request the Service Locator for a microservice that can serve `/hello/steve`. The Service Locator will respond with the host and port of the microservice where the request can be fulfilled. Finally the Service Gateway will perform the request and return the result to the browser. In this model, the browser only needs to know where the Service Gateway is. When using server-side discovery, a call on a Service Descriptor can only be reached if its call is added to the ACL.

This section uses HTTP-REST for all the examples but the concepts apply to any kind of traffic be it HTTP, binary over tcp, etc...


## Locatable Services

Lagom also supports disabling locatability of a Service Descriptor. It is a common practice to include infrastructure features in each microservice. Those infrastructure features are published via a Service Descriptor. Despite that, we usually don't want the infrastructure features to be locatable, in that case we can simply setup the Service Descriptor as non locatable which will tell the registrator to ignore it.