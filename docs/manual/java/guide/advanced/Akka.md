# Advanced Topic: Integrating with Akka

Lagom is built with Akka as one of the underlying technologies.  Nonetheless, writing simple Lagom services doesn't require interacting with Akka directly.

More advanced users may want direct access, as described in this section.

## Usage from Service Implementation

Pretty much everything in Akka is accessible through an `ActorSystem` object. You can inject the current `ActorSystem` into your service implementations or persistent entities with ordinary dependency injection.

Let's look at an example of a `WorkerService` that accepts job requests and delegates the work to actors running on other nodes in the service's cluster.

@[service-impl](code/docs/home/actor/WorkerServiceImpl.java)

Notice how the `ActorSystem` is injected through the constructor. We create worker actors on each node that has the "worker-node" role. We create a consistent hashing group router that delegates jobs to the workers. Details on these features are in the [Akka documentation](https://doc.akka.io/docs/akka/2.6/?language=java).

The worker actor looks like this:

@[actor](code/docs/home/actor/Worker.java)

The messages are ordinary [[Immutable Objects|Immutable]]. Note that they extend `Jsonable` since they need proper [[Serialization|Serialization]] when they are sent across nodes in the cluster of the service:

@[msg](code/docs/home/actor/AbstractJob.java)

@[msg](code/docs/home/actor/AbstractJobAccepted.java)

@[msg](code/docs/home/actor/AbstractJobStatus.java)

## Usage of Lagom APIs in Actors

If you need to have access to some Lagom API from an actor, you have two options:

1. Pass the Lagom object as an ordinary constructor parameter when creating the actor.
2. Use the `AkkaGuiceSupport` from the Play Framework.

The first alternative is probably sufficient in many cases, but we will take a closer look at the more advanced second alternative.

In your Guice module you add `AkkaGuiceSupport` and use the `bindActor` method, such as:

@[module](code/docs/home/actor/Worker2Module.java)

That allows the actor itself to receive injected objects. It also allows the actor ref for the actor to be injected into other components. This actor is named `worker` and is also qualified with the `worker` name for injection.

You can read more about this and how to use dependency injection for child actors in the [Play documentation](https://playframework.com/documentation/2.7.x/JavaAkka#Dependency-injecting-actors).

Adjusting the `Worker` actor from the previous section to allow injection of the `PubSubRegistry`:

@[actor](code/docs/home/actor/Worker2.java)

With the `PubSubRegistry` we can publish updates of the progress of the jobs to all nodes in the cluster, as described in [[Publish-Subscribe|PubSub]].

To make the example complete, an adjusted service implementation follows. Worker actors are created not by the service implementation, but by the `WorkerModule`. We have also added a `status` method that provides a stream of `JobStatus` values that clients can listen to.

@[service-impl](code/docs/home/actor/WorkerService2Impl.java)

## Updating Akka version

If you want to use a newer version of Akka, one that is not used by Lagom yet, you can add the following to your `build.sbt` file:

@[akka-update](code/build-update-akka.sbt)

Of course, other Akka artifacts can be added transitively. Use [sbt-dependency-graph](https://github.com/jrudolph/sbt-dependency-graph) to better inspect your build and check which ones you need to add explicitly.

> **Note:** When doing such updates, keep in mind that you need to follow Akka's [Binary Compatibility Rules](https://doc.akka.io/docs/akka/2.6/common/binary-compatibility-rules.html). And if you are manually adding other Akka artifacts, remember to keep the version of all the Akka artifacts consistent since [mixed versioning is not allowed](https://doc.akka.io/docs/akka/2.6/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed).

### Adding other Akka dependencies

If you want to use Akka artifacts that are not added transtively by Lagom, you can use `com.lightbend.lagom.core.LagomVersions.akka` to ensure all the artifacts will use a consistent version. For example:

@[akka-other-artifacts](code/build-update-akka.sbt)

> **Note:** When resolving dependencies, sbt will get the newest one declared for this project or added transitively. It means that if Play depends on a newer Akka (or Akka HTTP) version than the one you are declaring, Play version wins. See more details about [how sbt does evictions here](https://www.scala-sbt.org/1.x/docs/Library-Management.html#Eviction+warning).
