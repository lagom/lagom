# Lagom build philosophy

When done right, adopting a reactive microservice architecture can drastically increase a development organization's productivity and velocity. Lagom offers a flexible build approach that supports individual and team development, and that can grow over time. You can put all of your microservices in a single sbt or Maven build (or project), each service can be in a separate build, or multiple builds can contain groups of logically related services. 

To choose an approach, consider the pros and cons:

* Single build for multiple services:
    * Development of new features often requires simultaneous work on multiple services.  Having those services in the same build makes for a frictionless development experience.
    * However, as the number of developers working on a build increases, it can slow down velocity if they get in each other's way.
* Multiple builds, each with a single service or small group of services:
    * Each microservice can be changed independently and move forward faster when isolated. At release time, dependent services can upgrade and use the updated service.
    * However, multiple builds add complexity. You need to publish services to make them available to other services. The implementation needs to import all services on which an individual service depends. [[Splitting a system into multiple builds|MultipleBuilds]] describes how to deal with this. 

When starting out, it often makes sense to keep all services in the same build. A small team can easily work with one build and avoid the lag time and dependencies that arise when systems are divided into multiple builds. As a system and organization evolves, it can make sense to change your approach. For example, as a system grows in functionality, you can split services into different builds. The same principle applies to the teams working on them. It's important not to be afraid to refactor your builds to keep up with your organizational needs.








