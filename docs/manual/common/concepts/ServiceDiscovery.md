# Registering and discovering services

To be resilient and scalable, a system must support location transparency.  This allows you to run instances of the same microservice on multiple hosts, to move instances from one host to another when failures occur, and to scale the number of hosts up and down as load changes. In such a reactive system where microservice instances keep moving, spawning and dying, both clients and other microservices need a way to locate available service instances. 

Lagom provides the following functionality for your microservice systems during development:

* [Service registration](#Service-registration)
* [Client-side service discovery](#Client-side-service-discovery)
* [Server-side service discovery](#Server-side-service-discovery)

*Note*: This page uses HTTP examples but the concepts apply to any kind of traffic such as binary over TCP.

## Service registration

A Service Registry collaborates with microservice instances to maintain an up-to-date lookup table. The table includes the host and port for each available microservice instance. As load changes, the system can spawn or destroy instances in any location while continuing to satisfy requests. You can design a system to enable microservices to self-register or you can use a [3rd Party Registration service](https://microservices.io/patterns/3rd-party-registration.html).

When booting a Lagom microservice instance, a registrar will register the name of the microservice, the URL and the names of the locatable Service Descriptors on the Service Registry so that they can be located. When powering down an instance of a service, the registrar will have to update the Service Registry too. Lagom's [[Developer Environment|DevEnvironment]] provides an implementation of a Service Registry and a registrar so you can run your microservices locally.
 
<!---The following illustrates service registration. (TBA) --->

Many available technologies provide Service Registry capabilities. You will need to choose and/or develop a Service Locator for your services to run in your deployment environment (see for example [Lagom ZooKeeper Service Locator](https://github.com/jboner/lagom-service-locator-zookeeper)). You may need to work out a way to plug your Lagom services with a registrar. 

## Client-side service discovery

From Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](https://info.lightbend.com/COLL-20XX-Reactive-Microservices-Architecture-RES-LP.html)

> Once the information about each service has been stored it can be made available through a Service Registry that services can use to look the information up—using a pattern called Client-Side Service Discovery.

Lagom creates service clients for each Service Descriptor so that applications can interact with Lagom Services. Suppose a non-Lagom app wants to consume a hello service. It can use the Welcome Service Client and simply invoke the `hello` method. The Welcome Service Client will use the Service Registry to find a valid URL where  `welcome` is available and fulfill the request. This approach requires using Lagom provided code. In production, the Service Locator plugged into your services will be an element participating in this Client-Side Discovery. See [[Integrating with non-Lagom services|IntegratingNonLagom]] for more information.

<!--- The following diagram illustrates client-side service discovery. (TBA) --->

## Server-side service discovery

From Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](https://info.lightbend.com/COLL-20XX-Reactive-Microservices-Architecture-RES-LP.html)

> Another strategy is to have the information stored and maintained in a load balancer [...] using a pattern called Server-Side Service Discovery.

If you can't embed a Service Locator on every client, you can use server-side service discovery. This pattern uses a Service Gateway to allow clients to consume endpoints provided by the Service Descriptors registration. In this model, the browser only needs to know where the Service Gateway is. When using server-side discovery, a call on a Service Descriptor can only be reached if its call is added to the ACL.

For example, a browser can display a hello message to a user by requesting the `/hello/steve` path from the Service Gateway. The Service Gateway will know which service provides that endpoint and will ask the Service Registry for an instance of that service. The Service Registry will respond with the host and port of the instance where the request can be fulfilled. Finally the Service Gateway will perform the request and return the result to the browser.  

<!--- The following diagram illustrates server-side service discovery. (TBA) -->

To simplify testing of server-side service discovery, the Lagom development environment starts all your services plus a Service Registry and a Service Gateway. 





