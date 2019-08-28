# Advanced Topic: Integrating with Akka

Lagom is built with Akka as one of the underlying technologies.  Nonetheless, writing simple Lagom services generally won't require interacting with Akka directly.

More advanced users may want direct access, as described in this section.

## Usage from Service Implementation

Most Akka functions are accessible through an `ActorSystem` object. You can inject the current `ActorSystem` into your service implementations or persistent entities with ordinary dependency injection.

Let's look at an example of a `WorkerService` that accepts job requests and delegates the work to actors running on other nodes in the service's cluster.

@[service-impl](code/Akka.scala)

Notice how the `ActorSystem` is injected through the constructor. We create worker actors on each node that has the "worker-node" role. We create a consistent hashing group router that delegates jobs to the workers. Details on these features are in the [Akka documentation](https://doc.akka.io/docs/akka/2.6/?language=scala).

The worker actor looks like this:

@[actor](code/Akka.scala)

The messages are ordinary case classes. Note that they extend `Jsonable` since they need proper [[Serialization|Serialization]] when they are sent across nodes in the cluster of the service, and the have formats created for them:

@[dataobjects](code/Akka.scala)

These formats needed to be added to the serialization registry, as described in the [[cluster serialization documentation|Serialization]].
