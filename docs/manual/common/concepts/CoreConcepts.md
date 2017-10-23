# Introduction to Lagom concepts

The Lagom framework includes libraries and a development environment that support you from development to deployment:

* During development, a single command builds your project and starts all of your services and the supporting Lagom infrastructure. It hot-reloads when you modify code. The development environment allows you to bring up a new service or join an existing Lagom development team in just minutes.
* You can create microservices using Java or Scala. Lagom offers an especially seamless experience for communication between microservices. Service location, communication protocols, and other issues are handled by Lagom transparently, maximizing convenience and productivity. Lagom supports Event sourcing and CQRS (Command Query Responsibility Segregation) for persistence.
* Lagom creates service clients for you (based on service descriptors), making it extremely easy to invoke and test endpoints.  
* Deploy on your platform of choice, such as:
    * [Lightbend Enterprise Suite](https://www.lightbend.com/platform/production), which makes it easy to deploy, scale, monitor, and manage Lagom services in a container environment. You can run the deployment and monitoring capabilities of Enterprise Suite on up to three nodes by simply registering with Lightbend. Obtain a [Lightbend Subscription](https://www.lightbend.com/platform/subscription) to receive world class support and the ability to run more nodes.
    * [Kubernetes](https://kubernetes.io), an open-source solution for container orchestration. See how to [deploy Lagom Microservices on Kubernetes] (https://developer.lightbend.com/guides/k8s-microservices/).

Designing a microservices system that achieves high scalability and manifests resilience in the face of unexpected failures is extremely difficult. Without a framework such as Lagom, you would need to deal with all of the complex threading and concurrency issues inherent in highly distributed systems. By using Lagom as it was designed to be used, you can avoid many of these pitfalls and increase productivity at the same time. But, rather than throwing everything out and starting anew, Lagom allows you to adopt a [reactive architecture](https://info.lightbend.com/COLL-20XX-Reactive-Microservices-Architecture-RES-LP.html) within existing constraints. For example, you can create microservices that:

* Interact with legacy systems and/or replace monolithic application functionality.
* Use Cassandra for persistence or your database of choice and/or integrate with other data stores. (Lagom's persistence APIs support Cassandra by default because it provides functionality such as sharding and read-side support that work well in a microservices system)  



The remaining topics in this section further introduce:

* Lagom system architecture:
    * [[Lagom design philosophy|LagomDesignPhilosophy]]
    * [[Polyglot systems|PolyglotSystems]]
* Lagom development environment: 
    * [[Overview|DevelopmentEnvironmentOverview]]
    * [[Build philosophy|BuildConcepts]]
    * [[Component technologies|ComponentTechnologies]]
    * [[API Overview|APIOverview]]
* Patterns to use in your system:
    * [[Designing your microservices system|MicroserviceSystemDesign]]
    * [[Sizing individual microservices|MicroserviceDesign]]
    * [[Internal and external communication|InternalAndExternalCommunication]]
    * [[Registering and discovering services|ServiceDiscovery]]
    * [[Using immutable objects|Immutable]]
    * [[Managing data persistence|ES_CQRS]]
    * [[Advantages of Event Sourcing|ESAdvantage]]
    * [[Separating reads from writes|ReadVsWrite]]
    * [[Deploying resilient, scalable systems|ScalableDeployment]]
    

@toc@
