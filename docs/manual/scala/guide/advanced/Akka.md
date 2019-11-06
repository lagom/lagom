# Advanced Topic: Integrating with Akka

Lagom is built with Akka as one of the underlying technologies.  Nonetheless, writing simple Lagom services generally won't require interacting with Akka directly.

More advanced users may want direct access, as described in this section.

## Usage from Service Implementation

Pretty much everything in Akka is accessible through an `ActorSystem` object. You can inject the current `ActorSystem` into your service implementations or persistent entities with ordinary dependency injection.

Let's look at an example of a `WorkerService` that accepts job requests and delegates the work to actors running on other nodes in the service's cluster.

@[service-impl](code/Akka.scala)

Notice how the `ActorSystem` is injected through the constructor. We create worker actors on each node that has the "worker-node" role. We create a consistent hashing group router that delegates jobs to the workers. Details on these features are in the [Akka documentation](https://doc.akka.io/docs/akka/2.6/?language=scala).

The worker actor looks like this:

@[actor](code/Akka.scala)

The messages are ordinary case classes. Note that they extend `Jsonable` since they need proper [[Serialization|Serialization]] when they are sent across nodes in the cluster of the service, and the have formats created for them:

@[dataobjects](code/Akka.scala)

These formats needed to be added to the serialization registry, as described in the [[cluster serialization documentation|Serialization]].

## Updating Akka version

If you want to use a newer version of Akka, one that is not used by Lagom yet, you can add the following to your `build.sbt` file:

@[akka-update](code/build-update-akka.sbt)

Of course, other Akka artifacts can be added transitively. Use [sbt-dependency-graph](https://github.com/jrudolph/sbt-dependency-graph) to better inspect your build and check which ones you need to add explicitly.

> **Note:** When doing such updates, keep in mind that you need to follow Akka's [Binary Compatibility Rules](https://doc.akka.io/docs/akka/2.6/common/binary-compatibility-rules.html). And if you are manually adding other Akka artifacts, remember to keep the version of all the Akka artifacts consistent since [mixed versioning is not allowed](https://doc.akka.io/docs/akka/2.6/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed).

### Adding other Akka dependencies

If you want to use Akka artifacts that are not added transtively by Lagom, you can use `com.lightbend.lagom.core.LagomVersions.akka` to ensure all the artifacts will use a consistent version. For example:

@[akka-other-artifacts](code/build-update-akka.sbt)

> **Note:** When resolving dependencies, sbt will get the newest one declared for this project or added transitively. It means that if Play depends on a newer Akka (or Akka HTTP) version than the one you are declaring, Play version wins. See more details about [how sbt does evictions here](https://www.scala-sbt.org/1.x/docs/Library-Management.html#Eviction+warning).
