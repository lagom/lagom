# Introduction to Lagom concepts



The Lagom framework supports you from development to deployment:

* You can create microservices using Java or Scala. Lagom offers an especially seamless experience for communication between microservices. Service location, communication protocols, and other issues are handled by Lagom transparently, maximizing convenience and productivity. Lagom supports Event sourcing and CQRS (Command Query Responsibility Segregation) for persistence.
* During development, a single command builds your project and starts all of your services and the supporting Lagom infrastructure. It hot-reloads when you modify code. The development environment allows you to bring up a new service or join an existing Lagom development team in just minutes.
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
    * [[Basic design principles|Microservices]]
    * [[Registering and discovering services|ServiceDiscovery]]
    * [[Using immutable objects|Immutable]]
    * [[Managing data persistence|ES_CQRS]]
    

@toc@
