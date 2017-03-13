# Introduction to Lagom concepts

Designing a microservices system that achieves high scalability and manifests resilience in the face of unexpected failures is extremely difficult. Without a framework such as Lagom, you would need to deal with all of the complex threading and concurrency issues inherent in highly distributed systems. By using Lagom as it was designed to be used, you can avoid many of these pitfalls and increase productivity at the same time. The Lagom framework includes libraries and a development environment that support you from development to deployment:


* During development, a single command builds your project and starts all of your services and the supporting Lagom infrastructure. It hot-reloads when you modify code. The development environment allows you to bring up a new service or join an existing Lagom development team in just minutes.
* Create microservices using Java or Scala. Lagom offers an especially seamless experience for communication between microservices. Service location, communication protocols, and other issues are handled by Lagom transparently, maximizing convenience and productivity. Lagom supports Event sourcing and CQRS (Command Query Responsibility Segregation) for persistence.
* Deploy on your platform of choice. To simplify deployment, Lagom supports [the Lightbend Production Suite](http://lightbend.com/platform/production) out-of-the-box. The Production Suite provides a simple way to deploy, scale, monitor, and manage Lagom services in a container environment.

The remaining topics in this section further introduce:

* Lagom system architecture:
    * [[Lagom design philosophy|LagomDesignPhilosophy]]
    * [[Polyglot systems|PolyglotSystems]]
* Lagom development environment: 
    * [[Overview|DevEnvOvr]]
    * [[Build philosophy|BuildConcepts]]
    * [[Component technologies|ComponentTechnologies]]
* Patterns to use in your system:
    * [[Designing your microservices system|MicroserviceSystemDesign]]
    * [[Sizing individual microservices|MicroserviceDesign]]
    * [[Registering and discovering services|ServiceDiscovery]]
    * [[Using immutable objects|Immutable]]
    * [[Separating reads from writes|ReadVWrite]]
    * [[Managing data persistence|ES_CQRS]]
    * [[Advantages of Event Sourcing|ESAdvantage]]
    * [[Deploying resilient, scalable systems|ScalableDeployment]]
    

@toc@
