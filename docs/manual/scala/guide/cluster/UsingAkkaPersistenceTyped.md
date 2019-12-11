# Domain Modelling with Akka Persistence Typed

This section presents all the steps to model an [Aggregate](https://martinfowler.com/bliki/DDD_Aggregate.html), as defined in Domain-Driven Design, using [Akka Persistence Typed](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Scala) and following the [[CQRS|ES_CQRS]] principles embraced by Lagom.

Akka Persistence Typed provides an API for building distributed event-sourced Actors. We call it an Event Sourced Entity. By applying a few design rules, we can design CQRS Aggregates on top of Akka's API. We will refer to the term **Aggregate** whenever we cover a DDD Aggregate concept. Otherwise the term **Entity** or **Event Sourced Entity** will be used.

We use a simplified shopping cart example to guide you through the process. You can find a full-fledged shopping cart sample on our [samples repository](https://github.com/lagom/lagom-samples/tree/1.6.x/shopping-cart/shopping-cart-scala).

## Encoding the model

Start by defining your model in terms of Commands, Events, and State.

### Modelling the State

The state of the shopping cart is defined as following:

@[shopping-cart-state](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

Note that we are modeling it using a case class `ShoppingCart`, and there is a `checkedOutTime` that can be set when transitioning from one state (open shopping cart) to another (checked-out shopping cart). As we will see later, each state encodes the commands it can handle, which events it can persist, and to which other states it can transition.

> **Note**: The sample shown above is a simplified case. Whenever your model goes through different state transitions, a better approach is to have a trait and extensions of it for each state. See examples in the [style guide for Akka Persistence Typed](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html?language=Scala).

### Modelling Commands and Replies

Next, we define the commands that we can send to it.

Each command defines a [reply](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Scala#replies) through a `replyTo: ActorRef[R]` field where `R` is the reply *type* that will be sent back to the caller. Replies are used to communicate back if a command was accepted or rejected or to read the Entity data (ie: read-only commands). It is also possible to have a mix of both. For example, if the command succeeds, it returns some updated data; if it fails, it returns a rejected message. Or you can have commands without replies (ie: fire-and-forget). This is a less common pattern in CQRS Aggregate modeling though and not covered in this example.

@[shopping-cart-commands](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

In Akka Typed, unlike Akka classic and Lagom Persistence, it's not possible to return an exception to the caller. All communication between the actor and the caller must be done via the `replyTo:ActorRef[R]` passed in the command. Therefore, if you want to signal a rejection, you most have it encoded in your reply protocol.

The replies used by the commands above are defined like this:

@[shopping-cart-replies](code/docs/home/scaladsl/persistence/ShoppingCart.scala)


Here there are two different kinds of replies: `Confirmation` and `Summary`. `Confirmation` is used when we want to modify the state. A modification request can be `Accepted` or `Rejected`. Then, the `Summary` is used when we want to read the state of the shopping cart.

> **Note**: Keep in mind that `Summary` is not the shopping cart itself, but the representation we want to expose to the external world. It's a good practice to keep the internal state of the Entity private because it allows the internal state, and the exposed API to evolve independently.

### Modelling Events

Next, we define the events that our model will persist. The events must extend Lagom's [`AggregateEvent`](api/com/lightbend/lagom/scaladsl/persistence/AggregateEvent.html). This is important for tagging events. We will cover it soon in the [[tagging events|UsingAkkaPersistenceTyped#Tagging-the-events--Akka-Persistence-Query-considerations]] section.

@[shopping-cart-events](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

### Defining Commands Handlers

Once you've defined your model in terms of Commands, Replies, Events, and State, you need to specify the business rules. The command handlers define how to handle each incoming command, which validations must be applied, and finally, which events will be persisted if any.

You can encode it in different ways. The [recommended style](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html?language=Scala#command-handlers-in-the-state) is to add the command handlers in your state classes. For `ShoppingCart`, we can define the command handlers based on the two possible states:

@[shopping-cart-command-handlers](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

> **Note**: of course, it is possible to organize the command handlers in a way that you consider more convenient for your use case, but we recommend the `onCommand` pattern since it can help to keep the logic for each command well isolated.

Command handlers are the meat of the model. They encode the business rules of your model and act as a guardian of the model consistency. The command handler must first validate that the incoming command can be applied to the current model state. In case of successful validation, one or more events expressing the mutations are persisted. Once the events are persisted, they are applied to the state producing a new valid state.

Because a DDD Aggregate is intended to model a consistency boundary, it's not recommended validating commands using data that is not available in scope. Any decision should be solely based on the data passed in the commands and the state of the Aggregate. Any external call should be considered a smell because it means that the Aggregate is not in full control of the invariants it's supposed to be protecting.

There are two ways of sending back a reply: using `Effect.reply` and `Effect.persist(...).thenReply`. The first one is available directly on `Effect` and should be used when you reply without persisting any event. In this case, you can use the available state in scope because it's guaranteed not to have changed. The second variant should be used when you have persisted one or more events. The updated state is then available to you on the function used to define the reply.

You may run side effects inside the command handler. Please refer to [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Scala#effects-and-side-effects) for detailed information.

### Defining the Event Handlers

The event handlers are used to mutate the state of the Entity by applying the events to it. Event handlers must be pure functions as they will be used when instantiating the Entity and replaying the event journal. Similar to the command handlers, a [recommended style](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html?language=Scala#command-handlers-in-the-state) is to add them in the state classes.

@[shopping-cart-state-event-handlers](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

### EventSourcingBehaviour - gluing the bits together

With all the model encoded, the next step is to glue all the pieces together, so we can let it run as an Actor. To do that, define an `EventSourcedBehavior`. It's recommended to define an `EventSourcedBehavior` using `withEnforcedReplies` when modeling a CQRS Aggregate. Using [enforced replies](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Scala#replies) requires command handlers to return a `ReplyEffect` forcing the developers to be explicit about replies.

@[shopping-cart-create-behavior](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

The `EventSourcedBehavior.withEnforcedReplies` has four fields to be defined: `persistenceId`, `emptyState`, `commandHandler` and `eventHandler`.

The `persistenceId` defines the id that will be used in the event journal. The id is composed of a name (for example, `entityContext.entityTypeKey.name`) and a business id (for example, `entityContext.entityId`). These two values will be concatenated using a `"|"` by default (for example, `"ShoppingCart|123456"`). See [Akka's documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Scala#persistenceid) for more details.

> **Note**: The `entityContext` that appears in scope here will be introduced when covering `ClusterSharding` later in this guide.

The `emptyState` is the state used when the journal is empty. It's the initial state:

@[shopping-cart-empty-state](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

The `commandHandler` is a function `(State, Command) => ReplyEffect[Event, State]`. In this example, it's being defined using the `applyCommand` on the passed state. Equally, the `eventHandler` is a function `(State, Event) => Event` and defined in the passed state.

### Changing behavior -- Finite State Machines

If you are familiar with general Akka Actors, you are probably aware that after processing a message, you should return the next behavior to be used. With Akka Persistence Typed this happens differently. Command handlers and event handlers are all dependent on the current state, therefore you can change behavior by returning a new state in the event handler. Consult the [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Scala#changing-behavior) for more insight on this topic.

### Tagging the events -- Akka Persistence Query considerations

Events are persisted in the event journal and are primarily used to replay the state of the event-sourced entity each time it needs to be instantiated. However, in CQRS, we also want to consume those same events and generate read-side views or publish them in a message broker (eg: Kafka) for external consumption.

To be able to consume the events on the read-side, the events must be tagged. This is done using the `AggregateEventTag` utility. It's recommended to shard the tags so they can be consumed in a distributed fashion by Lagom's [[Read-Side Processor|ReadSide]] and [[Topic Producers|MessageBrokerApi#Implementing-a-topic]]. Although not recommended, it's also possible to not shard the events as explained [[here|ReadSide#Event-tags]].

This example splits the tags into 10 shards and defines the event tagger in the companion object of `ShoppingCartEvent`. Note that the tag name must be stable, as well as the number of shards. These two values can't be changed later without migrating the journal.

@[shopping-cart-events-object](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

The `AggregateEventTag` is a Lagom class used by Lagom's [[Read-Side Processor|ReadSide]] and [[Topic Producers|MessageBrokerApi#Implementing-a-topic]], however Akka Persistence Typed expects a function `Event => Set[String]`. Therefore, we need to use an adapter to transform Lagom's `AggregateEventTag` to the required Akka tagger function.

@[shopping-cart-create-behavior-with-tagger](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

### Configuring snapshots

Snapshotting is a common optimization to avoid replaying all the events since the beginning.

You can define snapshot rules in two ways: by predicate and by counter. Both can be combined. The example below uses a counter to illustrate the APIs. You can find more details on the [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence-snapshot.html?language=Scala).

@[shopping-cart-create-behavior-with-snapshots](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

## Akka Cluster Sharding

Lagom uses [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Scala) to distribute the event-sourced entities across all the nodes and guarantee that, at any single time, there is only one instance of a given Entity loaded in memory over the whole cluster.

### Creating the Entity instance

The event-sourced behavior needs to be initialized on the `ClusterSharding` before it's used. That process won't create any specific Entity instance, and it will only create the Shard Regions and prepare it to be used (read more about Shard Regions in the [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Scala) docs).

You must define an `EntityTypeKey` and a function of `EntityContext[Command] => Behavior[Command]` to initialize the `EventSourcedBehavior` for the Shopping Cart Entity.

The `EntityTypeKey` has as name to uniquely identify this model in the cluster. It should be typed on `ShoppingCartCommand` which is the type of the messages that the Shopping Cart can receive.

In the companion object of `ShoppingCart`, define the `EntityTypeKey` and factory method to initialize the `EventSourcedBehavior` for the Shopping Cart Entity.

@[companion-with-type-key-and-factory](code/docs/home/scaladsl/persistence/ShoppingCart.scala)

> **Note**: [Akka style guide](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html?language=Scala) recommends having an `apply` factory method in the companion object.

Finally, initialize the Entity on the `ClusterSharding` using the `typedKey` and the `behavior`. Lagom provides an instance of the `clusterSharding` extension through dependency injection for your convenience. Initializing an entity should be done only once and, in the case of Lagom, it is typically done in the `LagomApplication`:

@[shopping-cart-loader](code/docs/home/scaladsl/persistence/ShoppingCartLoader.scala)

### Getting instances of the Entity

To access instances of the Entity (which may be running locally or remotely on the cluster), you should inject the `ClusterSharding` on your service:

@[shopping-cart-service-impl](code/docs/home/scaladsl/persistence/ShoppingCartLoader.scala)

And then you can instantiate an `EntityRef` using the method `entityRefFor`. In our case, the `EntityRef` is typed to only accept `ShoppingCartCommand`s.

@[shopping-cart-entity-ref](code/docs/home/scaladsl/persistence/ShoppingCartLoader.scala)

To locate the correct actor across the cluster, you need to specify the `EntityTypeKey` we used to initialize the entity and the `id` for the instance we need. Akka Cluster will create the required actor in one node on the cluster or reuse the existing instance if the actor has already been created and is still alive.

The `EntityRef` is similar to an `ActorRef` but denotes the actor is sharded. Interacting with an `EntityRef` implies the messages exchanged with the actor may need to travel over the wire to another node.

#### Considerations on using the ask pattern

Since we want to send commands to the Entity and these commands declare a reply we will need to use the [ask pattern](https://doc.akka.io/docs/akka/2.6/typed/interaction-patterns.html?language=Scala#request-response).

The code we introduced below creates an `EntityRef` from inside the `ShoppingCartServiceImpl` meaning we are calling the actor (the `EntityRef`) from outside the `ActorSystem`. `EntityRef` provides an `ask()` overload out of the box meant to be used from outside actors.

@[shopping-cart-service-call](code/docs/home/scaladsl/persistence/ShoppingCartLoader.scala)

So we declare an implicit `timeout` and then invoke `ask` (which uses the timeout implicitly). The `ask` method accepts a function of `ActorRef[Res] => M` in which `Res` is the expected response type and `M` is the message being sent to the actor. The `ask` method will create an instance of `ActorRef[Res]` that can be used to build the outgoing message (command). Once the response is sent to `ActorRef[Res]`, Akka will complete the returned `Future[Res]` with the response (in this case `Future[Summary]`).

Finally, we operate over the `cartSummary` (in this case, we map it to a different type, ie: `ShoppingCartView`).

The `ShoppingCartView` and `asShoppingCartView` are defined as:

@[shopping-cart-service-view](code/docs/home/scaladsl/persistence/ShoppingCartLoader.scala)

@[shopping-cart-service-map](code/docs/home/scaladsl/persistence/ShoppingCartLoader.scala)

> **Note**: We are keeping the internal state of the Entity isolated from the exposed service API so they can evolve independently.

### Configuring number of shards

Akka recommends, as a rule of thumb, that the number of shards should be a factor ten higher than the planned maximum number of cluster nodes. It doesn't have to be exact. Fewer shards than the number of nodes will result in that some nodes will not host any shards. Too many shards will result in less efficient management of the shards, e.g. rebalancing overhead, and increased latency because the coordinator is involved in the routing of the first message for each shard.

See the [Akka Cluster Sharding documentation](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Scala#shard-allocation) for details on how to configure the number of shards.

### Configuring Entity passivation

Keeping all the Entities in memory all the time is inefficient. Entity passivation allows removal from the cluster when they've been unused for some time.

Akka supports both [programmatic passivation](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Scala#passivation) and [automatic passivation](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html?language=Scala#automatic-passivation). The default values for automatic passivation are generally good enough.

## Data Serialization

The messages (commands, replies) and the durable classes (events, state snapshots) need to be serializable to be sent over the wire across the cluster or be stored on the database. Akka recommends [Jackson-based serializers](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html?language=Scala) --preferably JSON, but CBOR is also supported-- as a good default in most cases. On top of Akka serializers, Lagom makes it easy to add [[Play-JSON serialization|SerializationPlayJson]] support, which may be more familiar to some Scala developers.

In [Akka Persistence Typed](https://doc.akka.io/docs/akka/2.6/typed/persistence.html?language=Scala), in particular, and when you adopt CQRS/ES practices, commands will include a `replyTo: ActorRef[Reply]` field. This `replyTo` field will be used on your code to send a `Reply` back, as shown in the examples above. Serializing an `ActorRef[T]` requires using the Akka Jackson serializer, meaning you cannot use Play-JSON to serialize commands.

The limitation to use Akka Jackson for Command messages doesn't apply to other messages like events, snapshots, or even replies. Each type Akka needs to serialize may use a different serializer.

Read more about the serialization setup and configuration in the [[serialization|Serialization]] section.
