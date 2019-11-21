# Migrating to Akka Persistence Typed

With the support for Akka Persistence Typed in Lagom it is possible to migrate existing code from Lagom Persistence (classic) to Akka Persistence Typed. There's a few steps to consider in order to be able to read an existing journal.

> **Note**: the only limitation when migrating from from Lagom Persistence (classic) to Akka Persistence Typed is that a full cluster shutdown is required. Even though all durable data is compatible, Lagom Persistence (classic) and Akka Persistence Typed can't coexist.

Before you start, make sure you have read the page [[Domain Modelling with Akka Persistence Typed|UsingAkkaPersistenceTyped]] and you understand how to model a domain using Akka Persistence Typed.

## Migrating the model

Similarly to Lagom's Persistent Entity, in Akka Persistence Typed you must create a class extending `EventSourcedBehaviorWithEnforcedReplies`.

@[akka-persistence-behavior-definition](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartEntity.java)

The `EventSourcedBehaviorWithEnforcedReplies` abstract class requires you to define the following:

* a `PersistenceId persistenceId`, to be passed to the `super` in its constructor
* an `emptyState()` method returning the `State` before any event was ever persisted
* a `commandHandler()` method to handle the commands, persist events and return a reply
* a `eventHandler()` method to handle events and mutate the `State`

This migration guide will not go into more details related to writing command and event handlers. Refer to the [Akka Persistence Typed docs](https://doc.akka.io/docs/akka/2.6/typed/index-persistence.html?language=Java) or the section on [[Domain Modelling with Akka Persistence Typed|UsingAkkaPersistenceTyped]] for more information.

### Commands

`Command` classes are the other set of classes most impacted by the migration. First, a `Command` will no longer need to extend the `PersistentEntity.ReplyType<R>` of the Lagom API. That type was used to specify a type `R` for the reply produced by the `Command`. To specify the type `R` of the reply add a `ActorRef<R> replyTo` field in the command.

__Before__:

@[akka-jackson-serialization-command-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-lagom-persistence/src/main/java/com/example/shoppingcart/impl/ShoppingCartCommand.java)

__After__:

@[akka-jackson-serialization-command-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartCommand.java)

The `ActorRef<R> replyTo` is necessary to know where to send the response to. It must be added to all command classes and adding it has implications on the serialization of those classes. Make sure to review the [[Serialization]] pages later in this reference documentation.

### Replies

In Akka Typed, it’s not possible to return an exception to the caller. All communication between the actor and the caller must be done via the `ActorRef<R> replyTo` passed in the command. Therefore, if you want to signal a rejection, you most have it encoded in your reply protocol.

See for example the `Confirmation` ADT below:

@[akka-persistence-typed-replies](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartCommand.java)

Then, all the command handlers must produce a `ReplyEffect`. For operations that don't mutate the model, use `Effect().reply` directly and for operations that persist events use `Effect().persist(...).thenReply` to create a `ReplyEffect` instance:

@[akka-persistence-typed-example-command-handler](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartEntity.java)

See [[Modelling Commands and Replies|UsingAkkaPersistenceTyped#Modelling-Commands-and-Replies]] for more details.

## Registration

In order to shard and distribute the `EventSourcedBehavior` instances across the cluster you will no longer use Lagom's `persistentEntityRegistry`. Instead, Lagom now provides direct access to `clusterSharding`, an instance of Akka's `ClusterSharding` extension you can use to initialize the sharding of `EventSourcedBehavior`.

__Before__, in the `ShoppingCartServiceImpl` class we'd use the Lagom provided `persistentEntityRegistry` instance to register a Guice provided instance:

@[akka-persistence-register-classic](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-lagom-persistence/src/main/java/com/example/shoppingcart/impl/ShoppingCartServiceImpl.java)

That registration can be removed.

__After__, we use the Lagom provided `clusterSharding` instance to initialize the sharding of the event source `Behavior` under the `ShoppingCartEntity.typeKey` identifier:

@[akka-persistence-init-sharded-behavior](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartServiceImpl.java)

To avoid `entityId` collisions across the cluster, initializing the sharding of a `Behavior` requires specifying an `EntityTypeKey` which acts as a namespacing. The `EntityTypeKey` is defined by a name and a type. The type indicates the kind of commands that can be sent to that sharded `Behavior`. In our example, we defined `typeKey` as a static field in the `ShoppingCartEntity` class:

@[akka-persistence-shopping-cart-object](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartEntity.java)

## Sending a command

In order to send commands to your `Behavior` instance you will have to obtain a reference to the actor where the `Behavior` is running and send commands to it.

__Before__:

@[akka-persistence-reffor-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-lagom-persistence/src/main/java/com/example/shoppingcart/impl/ShoppingCartServiceImpl.java)

__After__:

@[akka-persistence-reffor-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartServiceImpl.java)

That is, instead of injecting a `persistentEntityRegistry`, use a `clusterSharding` instance. Instead of getting a `PersistentEntityRef<T>` you will obtain an `EntityRef<T>`. Both `PersistentEntityRef<T>` and `EntityRef<T>` provide a method called `ask` but their signatures are different. `EntityRef<T>` is part of the API of Akka Cluster Sharding and it expects a `ActorRef<R> -> C` factory method which given a reference to a `replyTo` actor of type `ActorRef<R>` will produce a command `C` (see `reply -> Get(reply)` in the code above). Then the `ask` method also expects an implicit timeout. The result is a `CompletionStage<R>` with the reply instance produced in the `EventSourceBehavior`.

### Registration: caveats

Even though there is no longer a `PersistentEntity` instance to register, the `persistentEntityRegistry` is still necessary to build `TopicProducer`'s. When writing a `Service` implementation that includes a [[Topic Implementation|MessageBrokerApi#Implementing-a-topic]] the `TopicProducer` API requires an `eventStream` that is provided by the `persistentEntityRegistry`. This means that in some cases you will have to inject both the `persistentEntityRegistry` and the `clusterSharding`.

That is, even if you don't register a `PersistentEntity`, the events produced by Akka Persistence Typed are still compatible with `PersistentEntityRegistry.eventStream` as long as they are properly [[tagged|ReadSide#Event-tags]] so the projections ([[Read Sides|ReadSide]] and [[Topic Producers|MessageBrokerApi]]) don't change.

## Maintaining compatibility

Migrating to Akka Persistence Typed requires maintaining compatibility with data previously produced and persisted in the database journal. This requires focusing on three areas: [[Serialization]] of events, `PersistenceId` and tagging.

In order to be able to read existing events using Akka Persistence Typed you must use a `PersistenceId` that produces an identical `persistenceId` string as internally done by Lagom's PersistenceEntity's API.

@[shopping-cart-constructor](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartEntity.java)

The code above uses `PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId, "")`. There are three important pieces on that statement that we must review:

1. The first constructor argument must be the same value you used in Lagom Persistence (classic). This first argument is known as the `typeHint` and is used by the journal as a mechanism to avoid ID collision between different types. In Lagom Persistence (classic) the type hint defaults to the classname of your `PersistentEntity` but it can be [[overwritten|PersistentEntity#Refactoring-Consideration]] (review your code or the persisted data on your database). In our case, we are using `entityContext.entityTypeKey.name` because we defined the type key as `EntityTypeKey.create(ShoppingCartCommand.class, "ShoppingCartEntity")` where `"ShoppingCartEntity"` is the classname of the code we had in the implementation based on Lagom Persistence Classic.
2. The second argument must be the business id of your Aggregate. In this case, we can use `entityContext.entityId` because we're using that same business id for the sharded actor.
3. The third argument specifying a `separator`. Lagom Persistence Classic uses the an empty string `""` as a separator. When using Akka Persistence Typed we must explicitly set it to `""` for the Java API. The Akka default is `"|"`.

Even if you use the appropriate `PersistenceId`, you need to use a compatible serializer for your events. Read more about [[Serialization]] in this reference documentation..

Finally, only tagged events are readable by Lagom projections (either [[Read Sides|ReadSide]] and [[Topic Producers|MessageBrokerApi]]), and Lagom projections expect event tags to honour certain semantics. Finally, for events to be consumed in the correct order you must keep tagging the events in the same way as in your previous Lagom application.

Lagom provides an `AkkaTaggerAdapter` utility class that can be used to convert an existing Lagom `AggregateEventTag` to the appropriated tagging function expected by Akka Persistence Typed. When defining the `EventSourcedBehavior` specify a tagger by overriding the `tagsFor()` method and use the utility `AkkaTaggerAdapter.fromLagom` to convert your existing event tag:

@[akka-persistence-typed-lagom-tagger-adapter](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-java/shopping-cart-akka-persistence-typed/src/main/java/com/example/shoppingcart/impl/ShoppingCartEntity.java)
