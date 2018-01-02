# Designing your microservices system

Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](https://info.lightbend.com/COLL-20XX-Reactive-Microservices-Architecture-RES-LP.html) is fundamental reading for microservices systems architects. It takes time and experience to become skilled in this complex domain. Lagom provides practical guidance by offering opinionated APIs, implementations of supporting features, and appropriate defaults.

We recommend starting small. First, identify a need for a simple microservice that can consume asynchronous messages. It needn't be complex or even provide a lot of value. The simplicity reduces the risk associated with deployment and can provide a quick win. Next, at the architectural level, pull out a core service that can be compartmentalized. Divide it into a system of microservices. When you attack the problem a piece at a time, you and your team will learn as you go and will become increasingly effective. Employing approaches such as [Domain-driven Design](https://en.wikipedia.org/wiki/Domain-driven_design) (DDD) can help your organization deal with the complexity inherent in enterprise systems.

## Replacing a monolith

When designing a microservices system to replace an existing monolith, your approach can vary, depending on requirements and the existing architecture. For example, if the monolith is reasonably well-designed, you might be able to first de-couple legacy components and focus on migrating functionality a bit at a time. If a reasonable amount of downtime is not acceptable, you will need a detailed plan for switching functionality over from the old system to the new.

For example, imagine an enterprise monolithic application that handles the core business functions for many departments. Perhaps it has inventory functionality that is used by accounting, sales, marketing and operations --- each uses it in a different way. DDD encourages breaking such a large and unwieldy model into  [Bounded Contexts](https://martinfowler.com/bliki/BoundedContext.html). Each Bounded Context defines a boundary that applies to a particular team, addresses specific usage, and includes the data schema and physical elements necessary to materialize the system for that context. Use of Bounded Contexts allows small teams to focus on one context at a time and work in parallel. 

In the first implementation phase, you might modify the monolith to begin publishing inventory events that are of interest to the accounting department for a particular use case. The following diagram illustrates this as publishing to [Kafka](https://kafka.apache.org/intro), a streaming platform that supports publish/subscribe (pub/sub) and runs as a cluster for performance and reliability. 

[[MonolithPhaseOne.png]]

Next, as shown in the following image, you might create a microservice that subscribes to the topic and processes the data. Multiple instances of the microservice provide scalability and fault tolerance. The legacy functionality remains in place as you test and fine tune the microservice.

[[MonolithPhaseTwo.png]]

Next, as shown you could modify the monolith to make a remote HTTP call to the new microservice instead of calling its own internal business logic.

[[MonolithPhaseThree.png]]

When you are confident that the new microservice is performing well, you can remove the internal business logic from the monolith and move on to the next microservice, or set of microservices. This high-level example is just one of many ways you might choose to move functionality from a monolith.

<!---The following diagram illustrates a first try at decomposing a monolith. **A** represents a simple piece of functionality that was redesigned as a microservice. With the microservice instances online, code in the monolith that uses that functionality now locates available instances through the Service Gateway and communicates with them using the Kafka message broker. -->
