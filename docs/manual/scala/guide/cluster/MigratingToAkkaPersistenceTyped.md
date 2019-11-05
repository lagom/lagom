# Migrating to Akka Persistence Typed

With the support for Akka Persistence Typed in Lagom it is possible to migrate existing code from Lagom Persistence (classic) to Akka Persistence Typed. There's a few steps to consider and limitations to keep existing data still accessible.

> NOTE: the only limitation when migratin from from Lagom Persistence (classic) to Akka Persistence Typed is that a full cluster shutdown is required. Even though all durable data is compatible, Lagom Persistence (classic) and Akka Persistence Typed can't coexist.

## Migrating the model

Similarly to Lagom's Persistent Entity, to create an Akka Persistence Typed `EventSourcedBehavior` you need:

 * a `persistenceId: PersistenceId`
 * an `emptyState` which represents the `State` before any event was ever persisted
 * a function `(State, Command) => ReplyEffect` to handle the commands, persist events and return a reply
 * a function `(State, Event) => State` to handle events and mutate the `State`

@[akka-persistence-behavior-definition](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

So, instead of using a build and adding multiple command and event handlers, a single method for each is provided and inside it, a pattern matching is used.

@[akka-persistence-command-handler](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

This migration guide will not go into more details related to writing command and event handlers. Refer to the [Akka Persistence Typed docs](https://doc.akka.io/docs/akka/2.6/typed/index-persistence.html) or the section on [[domain modelling|DomainModelling]] for more information.

### Commands

`Command` classes are the other set of classes most impacted by the migration. First, a `Command` will no longer need to extend the `ReplyType[R]` of the Lagom API. That type was used to specify a type `R` for the reply produced by the `Command`. To specify the type `R` of the reply add a `replyTo: ActorRef[R]` field in the command.

_Before_:

@[akka-jackson-serialization-command-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-lagom-persistence/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

_After_:

@[akka-jackson-serialization-command-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

The `replyTo: ActorRef[R]` is necessary to know where to send the response to. It must be added to all command classes and adding it has implication on the serialization of those classes. Make sure to review the [[Serialization|MigratingToAkkaPersistenceTyped#Serialization]] section below and the [[Serialization]] pages later in this reference documentation.

Because the `EventSourcedBehavior` we're implementing uses `withEnforcedReplies` we no longer can fail a command throwing an exception and, instead, we must model failures with a `Reply`.

### Replies

Because we require enforced replies on the `EventSourcedBehavior` (see `.withEnforcedReplies` in the snippet above) we no longer can fail a command throwing an exception and, instead, we must model failures with a `Reply`.

See for example the `Confirmation` ADT below:

@[akka-persistence-typed-replies](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

Then, all the command handlers must produce a `ReplyEffect`. For failed operations use `Effect.reply` directly and for operations that produce events that need persisting use `Effect.persist` to create the `ReplyEffect` instance:

@[akka-persistence-typed-example-command-handler](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

## Registration

In order to shard and distribute the `EventSourcedBehavior` instances across the cluster you will no longer use Lagom's `persistentEntityRegistry`. Instead, Lagom now provides direct access to `clusterSharding`, an instance of Akka's `ClusterSharding` extension you use to initialize the sharding of `EventSourcedBehavior`.

_Before_, in the `ShoppingCartLoader.scala` class we'd use the Lagom provided `persistentEntityRegistry` instance to register a `macwire` provided instance:

@[akka-persistence-register-classic](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-lagom-persistence/src/main/scala/com/example/shoppingcart/impl/ShoppingCartLoader.scala)

That registration can be removed.

_After_, we use the Lagom provided `clusterSharding` instance to initialize the sharding of the event source `Behavior` under the `ShoppingCartState.typeKey` identifier:

@[akka-persistence-init-sharded-behavior](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartLoader.scala)

To avoid `entityId` collisions across the cluster, initializing the sharding of a `Behavior` requires specifying an `EntityTypeKey` which acts as a namespacing. The `EntityTypeKey` not only must provide a `name: String` but is also typed indicating which commands that sharded `Behavior` will accept. In our example, we defined `typeKey` in `object ShoppingCartState`:

@[akka-persistence-declare-entity-type-key](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

## Sending a command

In order to send commands to your `Behavior` instance you will have to obtain a reference to the actor where the `Behavior` run and send commands to it.

_Before_:

@[akka-persistence-reffor-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-lagom-persistence/src/main/scala/com/example/shoppingcart/impl/ShoppingCartServiceImpl.scala)


_After_:

@[akka-persistence-reffor-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartServiceImpl.scala)

That is, instead of injecting a `persistentEntityRegistry`, use a `clusterSharding` instance. Instead of getting a `PersistentEntityRef` you will obtain an `EntityRef[T]`. Both `PersistentEntityRef` and `EntityRef[T]` provide a method called `ask` but their signatures are different. `EntityRef[T]` is part of the API of Akka Cluster Sharding and it expects a `ActorRef[R] => C` factory method which given a reference to a `replyTo` actor of type `ActorRef[T]` will produce a command `C` (see `reply => Get(reply)` in the code above). Then the `ask` method also expects an implicit timeout. The result is a `Future[R]` with the reply instance produced in the `EventSourceBehavior`.

### Registration: caveats

Even though there is no longer a `PersistentEntity` instance to register, the `persistentEntityRegistry` is still necessary to build `TopicProducer`'s. When writing a `Service` implementation that includes a [[Topic Implementation|MessageBrokerApi#Implementing-a-topic]] the `TopicProducer` API requires an `eventStream` that is provided by the `persistentEntityRegistry`. This means in some cases you can't get rid of the `persistentEntityRegistry` wyou were injecting in your `Service` implementation even if you use the `clusterSharding` to obtaing a reference to your persistent behavior.

That is, even if you don't register a `PersistentEntity`, the events produced by Akka Persistence Typed are still compatible with `PersistentEntityRegistry.eventStream` as long as they are properly [[tagged|ReadSide#Event-tags]] so the projections ([[Read Sides|ReadSide]] and [[Topic Producers|MessageBrokerApi]]) don't change.

## Maintaining compatibility

Migrating to Akka Persistence Typed requires maintaining compatibility with data previously produced and persisted in the database journal. This requires focusing on three areas: [[De/Serialization|MigratingToAkkaPersistenceTyped#Serialization]] of events (detailed later), `PersistenceId` and tagging.

In order to be able to read existing events from Akka Persistence Typed you must use a `PersistenceId` that produces an identical `id: String` than what Lagom used internally.

@[akka-persistence-behavior-definition](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

The code above uses `PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)`. There are three important pieces on that statement that we must review:

1. the first argument of `PersistenceId.apply()` must be the same value you used in Lagom Persistence (classic). This first argument is known as the `typeHint` and is used by the journal as a mechanism to avoid ID collision between different types. In Lagom Persistence (classic) the type hint defaults to the classname of your `PersistentEntity` but it can be [[overwriten|PersistentEntity#Refactoring-Consideration]] (review your code or the persisted data on your database). In our case, we are using `entityContext.entityTypeKey.name` because we defined `EntityTypeKey[ShoppingCartCommand]("ShoppingCartEntity")` were `"ShoppingCartEntity"` is the classname of the code we had in the implementation based on Lagom Persistence Classic.
2. the second argument must be the business id of your Aggregate. In this case, we can use `entityContext.entityId` because we're using that same business id for the sharded actor.
3. an (optional) third argument specifying a `separator`. Lagom uses the `"|"` as a separator and, since `PersistenceId` also uses `"|"` as a default we're not specifying a separator.

Even if you use the appropriate `PersistenceId`, you need to use a compatible serializer for your events. Read more about [[De/Serialization|MigratingToAkkaPersistenceTyped#Serialization]] in the section below.

Finally, only tagged events are readable by Lagom projections (either [[Read Sides|ReadSide]] and [[Topic Producers|MessageBrokerApi]]), and Lagom projections expect event tags to honour certain semantics. Finally, for events to be consumed in the correct order, events produced by a certain instance must all be tagged identically. All these restrictions introduce limitations when using Akka Persistence Typed with a Journal containing data produced by Lagom Persistence classic.

Lagom provides appropriate tagging functions in the `AkkaTaggerAdapter` utility class. They must be explicitly registered but provide tagging functions that are compliant with Akka Persistence Typed while keeping backwards compatibility with Lagom Persistence classic. When implementing the factory method `EntityContext[T] => Behavior[T]` specify a tagger using `withTagger` with the `AkkaTaggerAdapter.fromLagom`:

@[akka-persistence-typed-lagom-tagger-adapter](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

## Serialization

All the classes sent over the wire or stored on the database will still need to be serializable. Persisted events will still have to be read.

Existing code creating and registering serializers is 100% valid except for `Command` classes. In Akka Typed, it is required to add a `replyTo: ActorRef[Reply]` field on messages that need a rference to reply back (like `Commands`). In order to serialize a class that includes an `ActorRef[T]` field the class must use the Akka Jackson serializer. Read more on the [[serialization|Serialization]] section of the docs.

To convert your `Command` classes to use Akka Jackson serialization instead of Lagom Play-JSON you need to follow these steps:

First,create a marker trait. For example:

@[akka-jackson-serialization-marker-trait](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

Then, use the regular Akka serialization binding mechanism so all classes extending that trait use the Akka Jackson JSON serializer:

@[akka-jackson-serialization-binding](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/resources/application.conf)

Then, remove all code that's `play-json` from your Command classes and companion objects.

_Before_:

@[akka-jackson-serialization-command-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-lagom-persistence/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

_After_:

@[akka-jackson-serialization-command-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

Note how the type of the reply is no longer specified via `ReplyType[T]` but as the type of the protocol the `replyTo: ActorRef[T]` actor.

And finally, remove all commands from JsonSerialiserRegistry

@[akka-jackson-serialization-registry-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-lagom-persistence/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

@[akka-jackson-serialization-registry-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)
