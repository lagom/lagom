# Advanced Persistent Entity Usage

Below are some advanced usages of persistent entities.

## Child actors

Lagom's `PersistentEntityRegistry` creates and distributes persistent entities by running them in actors, distributed across a Lagom cluster using [Akka cluster sharding](https://doc.akka.io/docs/akka/current/cluster-sharding.html?language=scala). In some use cases, it may make sense to run persistent entities as a child actor of a user managed actor, which itself may be distributed using cluster sharding. Some examples of such use cases include:

* Persistent process managers. When you have a long running operation that involves multiple interactions with other systems or entities, it is often necessary to store the state of the process, so that it can be continued by an asynchronous signal (such as a message arriving through a message queue). Lagom's persistent entity API can be a good fit for this persistence, since it provides an audit log of the process, and it allows read side processors to do reconciliation on the process.
* Hybrid persistence solutions. Persistent entities are not suited to some persistence requirements, such as the need to store large blobs of data. In such cases it may make sense to store parts of the data that fit an event model in a persistent entity, while storing the blob data directly in a database designed for that. Updates would need to be idempotent with a durable retry mechanism if either failed.

Lagom provides a [`ChildPersistentEntityFactory`](api/com/lightbend/lagom/scaladsl/persistence/ChildPersistentEntityFactory.html) for instantiating persistent entities as a child of a user managed actor. This allows you to implement your process or other management logic in an actor, with the persistent entity being a direct child so that all communication with it is local, rather than needing to go through Akka cluster sharding to communicate with it.

### Uniqueness of persistent entities

It is of utmost importance that each persistent entity is only represented by one actor across the cluster at any time. `ChildPersistentEntityFactory` does not and cannot ensure the uniqueness of entities, it is up to the application code to use a cluster mechanism for doing this. The most straight forward way to do this is to have the parent actors distributed using Akka cluster sharding, and ensuring a 1:1 relationship of parent actors to persistent entities (based on, for example, the parent actors entity id). How to shard actors across a cluster is beyond the scope of this documentation, the [Akka cluster sharding documentation](https://doc.akka.io/docs/akka/current/cluster-sharding.html?language=scala) can be consulted for this.

### Creating a child persistent entity factory

The [`ChildPersistentEntityFactory`](api/com/lightbend/lagom/scaladsl/persistence/ChildPersistentEntityFactory.html) can be created by supplying the factory for creating the entity instance. This is typically created by the code that starts the parent actor and the cluster sharding instance that distributes it. Once created, the factory can then be passed to created actors via their constructors, through an Akka `Props`:

@[create-child-persistent-entity-factory](code/ChildActors.scala)

The reason the factory gets created outside of the actor is that this allows mocked factories to be injected, which can be backed by Actor probes rather than an actual database backed entity.

### Creating a child persistent entity

Inside an actor that has a `ChildPersistentEntityFactory`, you can create entities. This is often done in the constructor or `preStart` function of the entity:

@[create-child-persistent-entity](code/ChildActors.scala)

The first parameter is the ID of the entity, it must be unique across the entire cluster. The name, `entity`, is the name of the child actor, and only has to be unique to that actor.

### Using a child persistent entity

`ChildPersistentEntityFactory.apply` returns a [`ChildPersistentEntity`](api/com/lightbend/lagom/scaladsl/persistence/ChildPersistentEntity.html). This provides a number of methods, mirroring the methods available for communicating with actors.

The `!` method can be used to send a message from the current actor:

@[child-persistent-entity-tell](code/ChildActors.scala)

Similar to `!`, `forward` can also be used to send a message, the difference being that the sender of the message will be the sender of the current message being processed in the parent actor:

@[child-persistent-entity-forward](code/ChildActors.scala)

Finally `?` can be used to get a future of the reply sent to the message:

@[child-persistent-entity-ask](code/ChildActors.scala)

### Shutting a child entity down

When rebalancing a cluster, it's important that graceful shutdown of entity actors is used to ensure all persistence operations have complete before the entity is started up on another cluster. To gracefully shut a child persistent entity down, the `stop` method can be used to tell it to shut down. The `actor` method can then be used to obtain a reference to the actor, which can then be watched, and when it does shut down, the parent actor can shut itself down.

### Testing actors that use child persistent entities

The `ChildPersistentEntityFactory` conveniently allows child persistent entity actors to be mocked, by supplying an `ActorRef` to `ChildPersistentEntityFactory.mocked`. This `ActorRef` can be an Akka TestKit probe, which allows you to run assertions about what messages were sent to the entity, and simulate responses back. Here's an example of how to use it:

@[mock-child-persistent-entity](code/ChildActors.scala)
