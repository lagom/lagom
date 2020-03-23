# Domain Modelling with Akka Persistence Typed

This section presents all the steps to model an [Aggregate](https://martinfowler.com/bliki/DDD_Aggregate.html), as defined in Domain-Driven Design, using [Akka Persistence Typed](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Java) and following the [[CQRS|ES_CQRS]] principles embraced by Lagom.

Akka Persistence Typed provides an API for building distributed event-sourced Actors. We call it an Event Sourced Entity. By applying a few design rules, we can design CQRS Aggregates on top of Akka's API. We will refer to the term **Aggregate** whenever we cover a DDD Aggregate concept. Otherwise the term **Entity** or **Event Sourced Entity** will be used.


We use a simplified shopping cart example to guide you through the process. You can find a full-fledged shopping cart sample on our [samples repository](https://github.com/lagom/lagom-samples/tree/1.6.x/shopping-cart/shopping-cart-java).

## Encoding the model

First, create a class (ie: `ShoppingCartEntity`) extending the abstract class `EventSourcedBehaviorWithEnforcedReplies`.  It's recommended to use `EventSourcedBehaviorWithEnforcedReplies` when modeling a CQRS Aggregate. Using [enforced replies](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Java#replies) requires command handlers to return a `ReplyEffect` forcing the developers to be explicit about replies.

@[shopping-cart-entity](code/docs/home/persistence/ShoppingCartEntity.java)

`EventSourcedBehaviorWithEnforcedReplies` has three type parameters (Command, Event and State) and a few mandatory and optional methods that will be implemented as we progress through the guide.

All model classes (`ShoppingCartEntity.Command`, `ShoppingCartEntity.Event`, `ShoppingCartEntity.ShoppingCart`) will be defined as static inner classes of `ShoppingCartEntity`.

### Modelling the State

The state of the shopping cart is defined as following:

@[shopping-cart-state](code/docs/home/persistence/ShoppingCartEntity.java)

The `ShoppingCart` has a `checkedOutTime` that can be set when transitioning from one state (open shopping cart) to another (checked-out shopping cart). As we will see later, each state encodes the commands it can handle, which events it can persist, and to which other states it can transition.

> **Note**: The sample shown above is a simplified case. Whenever your model goes through different state transitions, a better approach is to have an interface and implementations of it for each state. See examples in the [style guide for Akka Persistence Typed](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html?language=Java).

### Modelling Commands and Replies

Next, we define the commands that we can send to it.

Each command defines a [reply](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Java#replies) through a `ActorRef<R> replyTo` field where `R` is the reply *type* that will be sent back to the caller. Replies are used to communicate back if a command was accepted or rejected or to read the Entity data (ie: read-only commands). It is also possible to have a mix of both. For example, if the command succeeds, it returns some updated data; if it fails, it returns a rejected message. Or you can have commands without replies (ie: `fire-and-forget`). This is a less common pattern in CQRS Aggregate modeling though and not covered in this example.

@[shopping-cart-commands](code/docs/home/persistence/ShoppingCartEntity.java)

In Akka Typed, unlike Akka classic and Lagom Persistence, it's not possible to return an exception to the caller. All communication between the actor and the caller must be done via the `ActorRef<R> replyTo` passed in the command. Therefore, if you want to signal a rejection, you most have it encoded in your reply protocol.

The replies used by the commands above are defined like this:

@[shopping-cart-replies](code/docs/home/persistence/ShoppingCartEntity.java)

Here there are two different kinds of replies: `Confirmation` and `Summary`. `Confirmation` is used when we want to modify the state. A modification request can be `Accepted` or `Rejected`. Then, the `Summary` is used when we want to read the state of the shopping cart.

> **Note**: Keep in mind that `Summary` is not the shopping cart itself, but the representation we want to expose to the external world. It's a good practice to keep the internal state of the Entity private because it allows the internal state, and the exposed API to evolve independently.

### Modelling Events

Next, we define the events that our model will persist. The events must extend Lagom's [`AggregateEvent`](api/com/lightbend/lagom/javadsl/persistence/AggregateEvent.html). This is important for tagging events. We will cover it soon in the [[tagging events|UsingAkkaPersistenceTyped#Tagging-the-events--Akka-Persistence-Query-considerations]] section.

@[shopping-cart-events](code/docs/home/persistence/ShoppingCartEntity.java)

### Defining Commands Handlers

Once you've defined your model in terms of Commands, Replies, Events, and State, you need to specify the business rules. The command handlers define how to handle each incoming command, which validations must be applied, and finally, which events will be persisted if any.

You can encode it in different ways. The [recommended style](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html#command-handlers-in-the-state?language=Java) is to add the command handlers in your state classes. For `ShoppingCart`, we can define the command handlers based on the two possible states:

@[shopping-cart-command-handlers](code/docs/home/persistence/ShoppingCartEntity.java)

> **Note**: of course, it is possible to organize the command handlers in a way that you consider more convenient for your use case, but we recommend the `onCommand` pattern since it can help to keep the logic for each command well isolated.

Command handlers are the meat of the model. They encode the business rules of your model and act as a guardian of the model consistency. The command handler must first validate that the incoming command can be applied to the current model state. In case of successful validation, one or more events expressing the mutations are persisted. Once the events are persisted, they are applied to the state producing a new valid state.

Because a DDD Aggregate is intended to model a consistency boundary, it's not recommended validating commands using data that is not available in scope. Any decision should be solely based on the data passed in the commands and the state of the Aggregate. Any external call should be considered a smell because it means that the Aggregate is not in full control of the invariants it's supposed to be protecting.

There are two ways of sending back a reply: using `Effect().reply` and `Effect().persist(...).thenReply`. The first one is available directly on `Effect` and should be used when you reply without persisting any event. In this case, you can use the available state in scope because it's guaranteed not to have changed. The second variant should be used when you have persisted one or more events. The updated state is then available to you on the function used to define the reply.

You may run side effects inside the command handler. Please refer to [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Java#effects-and-side-effects) for detailed information.

### Defining the Event Handlers

The event handlers are used to mutate the state of the Entity by applying the events to it. Event handlers must be pure functions as they will be used when instantiating the Entity and replaying the event journal. Similar to the command handlers, a [recommended style](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html?language=Java#command-handlers-in-the-state) is to add them in the state classes.

@[shopping-cart-event-handlers](code/docs/home/persistence/ShoppingCartEntity.java)

### EventSourcingBehaviour - gluing the bits together

With all the model encoded, the next step is to prepare `ShoppingCartEntity` so we can let it run as an Actor.

The `EventSourcedBehaviorWithEnforcedReplies` has a constructor receiving a `PersistenceId` that we need to call from `ShoppingCartEntity` own constructor. In order to build a `PersistenceId` instance we will need an `EntityContext<Command>` instance. We will add it as a constructor argument to `ShoppingCartEntity`.

The `persistenceId` defines the id that will be used in the event journal. The id is composed of a name (for example, `entityContext.entityTypeKey.name`) and a business id (for example, `entityContext.entityId`). These two values will be concatenated using a `"|"` by default (for example, `"ShoppingCart|123456"`). See [Akka's documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Java#persistenceid) for more details.

@[shopping-cart-constructor](code/docs/home/persistence/ShoppingCartEntity.java)

> **Note**: the constructor is `private` and there is a static method `create()` to create instances, as recommended by the [Akka style guide](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html?language=Java). This and the need for `EntityContext` will be explained when covering `ClusterSharding` later in this guide.

Morever, we also initialize a field called `tagger` using an `AkkaTaggerAdapter`. We will cover it soon in the [[tagging events|UsingAkkaPersistenceTyped#Tagging-the-events--Akka-Persistence-Query-considerations]] section.

Next we need to implement the `emptyState()` method. The `emptyState` is the state used when the journal is empty. It's the initial state:

@[shopping-cart-empty-state](code/docs/home/persistence/ShoppingCartEntity.java)

### Changing behavior -- Finite State Machines

If you are familiar with general Akka Actors, you are probably aware that after processing a message, you should return the next behavior to be used. With Akka Persistence Typed this happens differently. Command handlers and event handlers are all dependent on the current state, therefore you can change behavior by returning a new state in the event handler. Consult the [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Java#changing-behavior) for more insight on this topic.

### Tagging the events -- Akka Persistence Query considerations

Events are persisted in the event journal and are primarily used to replay the state of the event-sourced entity each time it needs to be instantiated. However, in CQRS, we also want to consume those same events and generate read-side views or publish them in a message broker (eg: Kafka) for external consumption.

To be able to consume the events on the read-side, the events must be tagged. This is done using the `AggregateEventTag` utility. It's recommended to shard the tags so they can be consumed in a distributed fashion by Lagom's [[Read-Side Processor|ReadSide]] and [[Topic Producers|MessageBrokerApi#Implementing-a-topic]]. Although not recommended, it's also possible to not shard the events as explained [[here|ReadSide#Event-tags]].

This example splits the tags into 10 shards and defines the event tagger in the `ShoppingCartEntity.Event` interface. Note that the tag name must be stable, as well as the number of shards. These two values can't be changed later without migrating the journal.

@[shopping-cart-event-tag](code/docs/home/persistence/ShoppingCartEntity.java)

> **Note**: if you're using a JDBC database to store your journal, the number of sharded tags (`NumShards`) should not be greater then 10. This is due to an existing [bug](https://github.com/dnvriend/akka-persistence-jdbc/issues/168) in the plugin. Failing to follow this directive will result in some events being delivered more than once on the read-side or topic producers.

The `AggregateEventTag` is a Lagom class used by Lagom's [[Read-Side Processor|ReadSide]] and [[Topic Producers|MessageBrokerApi#Implementing-a-topic]], however Akka Persistence Typed provides a method accepting an `Event` and returning a `Set<String>` to tag events before persisting them. Therefore, we need to use an adapter to transform Lagom's `AggregateEventTag` to the required Akka tagger function. As shown in the constructor section, we instantiate a `tagger` field using `AkkaTaggerAdapter`. This field can then be used when implementing the tagging method.

@[shopping-cart-akka-tagger](code/docs/home/persistence/ShoppingCartEntity.java)

### Configuring snapshots

Snapshotting is a common optimization to avoid replaying all the events since the beginning.

You can define snapshot rules in two ways: by predicate and by counter. Both can be combined. The example below uses a counter to illustrate the APIs. You can find more details on the [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence-snapshot.html?language=Java).

@[shopping-cart-create-behavior-with-snapshots](code/docs/home/persistence/ShoppingCartEntity.java)

## Akka Cluster Sharding

Lagom uses [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Java) to distribute the event-sourced entites across all the nodes and guarantee that, at any single time, there is only one instance of a given Entity loaded in memory over the whole cluster.

### Creating the Entity instance

The event-sourced behavior needs to be initialized on the `ClusterSharding` before it's used. That process won't create any specific Entity instance, and it will only create the Shard Regions and prepare it to be used (read more about Shard Regions in the [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Java) docs).

You must define an `EntityTypeKey` and a function of `EntityContext<Command> -> Behavior<Command>` to initialize the the Shopping Cart Entity.

The `EntityTypeKey` has as name to uniquely identify this model in the cluster. It should be typed on `ShoppingCartCommand` which is the type of the messages that the Shopping Cart can receive. The easiest is to define if as a static field in `ShoppingCartEntity`.

@[shopping-cart-type-key](code/docs/home/persistence/ShoppingCartEntity.java)

Finally, initialize the Entity on the `ClusterSharding` using the `typeKey` and the `create()` static method. Lagom provides an instance of the `clusterSharding` extension through dependency injection for your convenience. Initializing an Entity should be done only once and, in the case of Lagom, it is typically done in the constructor of the service implementation. You should inject the `ClusterSharding` on your service for that matter.

@[shopping-cart-init](code/docs/home/persistence/ShoppingCartServiceImpl.java)

### Getting instances of the Entity

To access instances of the Entity (which may be running locally or remotely on the cluster), you should use the injected the `ClusterSharding`. You can instantiate an `EntityRef` using the method `entityRefFor`. In our case, the `EntityRef` is typed to only accept `ShoppingCart.Command`s.

@[shopping-cart-entity-ref](code/docs/home/persistence/ShoppingCartServiceImpl.java)

To locate the correct actor across the cluster, you need to specify the `EntityTypeKey` we used to initialize the entity and the `id` for the instance we need. Akka Cluster will create the required actor in one node on the cluster or reuse the existing instance if the actor has already been created and is still alive.

The `EntityRef` is similar to an `ActorRef` but denotes the actor is sharded. Interacting with an `EntityRef` implies the messages exchanged with the actor may need to travel over the wire to another node.

#### Considerations on using the ask pattern

Since we want to send commands to the Entity and these commands declare a reply we will need to use the [ask pattern](https://doc.akka.io/docs/akka/2.6/typed/interaction-patterns.html?language=Java#request-response).

The code we introduced below creates an `EntityRef` from inside the `ShoppingCartServiceImpl` meaning we are calling the actor (the `EntityRef`) from outside the `ActorSystem`. `EntityRef` provides an `ask()` overload out of the box meant to be used from outside actors.

@[shopping-cart-service-call](code/docs/home/persistence/ShoppingCartServiceImpl.java)

So we declare an `askTimeout` and then invoke `ask`. The `ask` method accepts a function of `ActorRef<Res> -> M` in which `Res` is the expected response type and `M` is the message being sent to the actor. The `ask` method will create an instance of `ActorRef<Res>` that can be used to build the outgoing message (command). Once the response is sent to `ActorRef<Res>`, Akka will complete the returned `CompletionStage<Res>` with the response (in this case `CompletionStage<ShoppingCartSummary>`).

Finally, we operate over the `summary` (in this case, we map it to a different type, ie: `ShoppingCartView`, using the `thenApply` method).

The `ShoppingCartView` and `asShoppingCartView` are defined as:

@[shopping-cart-service-view](code/docs/home/persistence/ShoppingCartServiceImpl.java)

@[shopping-cart-service-map](code/docs/home/persistence/ShoppingCartServiceImpl.java)

> **Note**: We are keeping the internal state of the Entity isolated from the exposed service API so they can evolve independently.

### Configuring number of shards

Akka recommends, as a rule of thumb, that the number of shards should be a factor ten higher than the planned maximum number of cluster nodes. It doesn't have to be exact. Fewer shards than the number of nodes will result in that some nodes will not host any shards. Too many shards will result in less efficient management of the shards, e.g. rebalancing overhead, and increased latency because the coordinator is involved in the routing of the first message for each shard.

See the [Akka Cluster Sharding documentation](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Java#shard-allocation) for details on how to configure the number of shards.

### Configuring Entity passivation

Keeping all the Entities in memory all the time is inefficient. Entity passivation allows removal from the cluster when they've been unused for some time.

Akka supports both [programmatic passivation](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Java#passivation) and [automatic passivation](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Java#automatic-passivation). The default values for automatic passivation are generally good enough.

## Data Serialization

The messages (commands, replies) and the durable classes (events, state snapshots) need to be serializable to be sent over the wire across the cluster or be stored on the database. Akka recommends [Jackson-based serializers](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html?language=Java) --preferably JSON, but CBOR is also supported-- as a good default in most cases.

Read more about the serialization setup and configuration in the [[serialization|Serialization]] section.

## Testing

The section in [Testing](https://doc.akka.io/docs/akka/2.6/typed/persistence-testing.html?language=Java) covers all the steps and features you need to write unit tests for your Aggregates.
