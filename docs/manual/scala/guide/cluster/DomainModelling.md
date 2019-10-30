# Domain Modelling

This section presents all the steps to model an [Aggregate](https://martinfowler.com/bliki/DDD_Aggregate.html), as defined in Domain-Driven Design, using [Akka Persistence Typed](https://doc.akka.io/docs/akka/2.6/typed/persistence.html) and following the [[CQRS|ES_CQRS]] principles embraced by Lagom. While Akka Persistence Typed provides an API for building event-sourced actors, the same does not necessarily apply for CQRS Aggregates. To build CQRS applications, we need to applying a few rules to our design.

A simplified shopping cart example is used to guide you through the process. You can find a full-fledge shopping cart sample on our [samples repository](https://github.com/lagom/lagom-samples/tree/1.6.x/shopping-cart/shopping-cart-scala).

## Encoding the model

Start by defining your model in terms of Commands, Events, and State.

### Modelling the State

The state of the shopping cart is defined as following:

```scala
/** Common Shopping Cart trait */
sealed trait ShoppingCart
/** Once we add new items it becomes an open shopping cart */
case class OpenShoppingCart(items: Map[String, Int]) extends ShoppingCart
/** Once checked-out it reaches end-of-life and can't be used anymore */
case class CheckedOutShoppingCart(items: Map[String, Int]) extends ShoppingCart
```

Note that we are modelling it using a common trait `ShoppingCart` and two possible states modelled as extensions of `ShoppingCart`. This is a recommended technique whenever your model go through different state transitions. As we will see later, each state encodes which commands it can handle, which events it can persist and to which other states it can transition.

### Modelling Commands and Replies

Next we define the commands that we can send to it.

Each command defines a [reply](https://doc.akka.io/docs/akka/2.6/typed/persistence.html#replies) through a `replyTo: ActorRef[R]` field where `R` is the reply that will be sent back to the caller. Replies are used to communicate back if a command was accepted or rejected or to read the aggregate data (ie: read-only commands). It's also possible to have a mix of both, for example, if the command succeeds it returns some updated data; if it fails it returns a rejected message. Or you can have commands without replies (ie: fire-and-forget). This is a less common pattern in CQRS Aggregate modeling though and not covered in this example.

In Akka Typed, it's not possible to return an exception to the caller. All communication between the actor and the caller must be done via the `replyTo:ActorRef[R]` passed in the command. Therefore, if you want to signal a rejection, you most have it encoded in your reply protocol.

```scala
// Replies
sealed trait Confirmation
case object Accepted extends Confirmation
final case class Rejected(reason: String) extends Confirmation
final case class ShoppingCartSummary(items: Map[String, Int], checkedOut: Boolean)

// Commands
sealed trait ShoppingCartCommand

final case class AddItem(itemId: String,
                         quantity: Int,
                         replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class RemoveItem(itemId: String,
                            replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class AdjustItemQuantity(itemId: String,
                                    quantity: Int,
                                    replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class Checkout(replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class Get(replyTo: ActorRef[ShoppingCartSummary]) extends ShoppingCartCommand
```

Note that we have different kinds of replies: `Confirmation` used when we want to modify the state. A modification request can be `Accepted` or `Rejected`. And `ShoppingCartSummary` used when we want to read the state of the shopping cart. Keep in mind that `ShoppingCartSummary` is not the shopping cart itself, but the representation we want to expose to the external world. It's a good practice to keep the internal state of the aggregate private because it allows the internal state, and the exposed API to evolve independently.

### Modelling Events

Next, we define the events that our model will persist. The events must extend Lagom's `AggregateEvent`. This is important for tagging events. We will cover this topic a little further.

```scala
sealed trait ShoppingCartEvent extends AggregateEvent[ShoppingCartEvent] {
  def aggregateTag = ShoppingCartEvent.Tag
}

final case class ItemAdded(itemId: String, quantity: Int)
  extends ShoppingCartEvent

final case class ItemRemoved(itemId: String)
  extends ShoppingCartEvent

final case class ItemQuantityAdjusted(itemId: String, newQuantity: Int)
  extends ShoppingCartEvent

final case object CartCheckedOut extends ShoppingCartEvent
```

### Defining Commands Handlers

Once you define your protocol in terms of Commands, Replies, Events and State, you need to specify the business rules of your model. The command handlers define how to handle each incoming command, which validations must be applied, and finally, which events will be persisted, if any.

You can encode it in different ways. The [recommended style](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html#command-handlers-in-the-state) is to add the command handlers in your state classes. Since `ShoppingCart` has two state class extensions, it makes sense to add the respective business rule validations on each state class. Each possible state will define how each command should be handled.

```scala
sealed trait ShoppingCart  {
  def applyCommand(cmd: ShoppingCartCommnad): ReplyEffect[ShoppingCartEvent, ShoppingCart]
}

case class OpenShoppingCart(items: Map[String, Int]) extends ShoppingCart {
  def applyCommand(cmd: ShoppingCartCommnad) =
    cmd match {

      case AddItem(itemId, quantity, replyTo) =>
        if (items.contains(itemId))
          Effect.reply(replyTo)(Rejected(s"Item '$itemId' was already added to this shopping cart"))
        else if (quantity <= 0)
          Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))
        else
          Effect
            .persist(ItemAdded(itemId, quantity))
            .thenReply(replyTo) { updatedCart => // updatedCart is the state updated after applying ItemUpdated
              Accepted
            }

      case RemoveItem(itemId, replyTo) =>
        if (items.contains(itemId))
          Effect
            .persist(ItemRemoved(itemId))
            .thenReply(replyTo)(_ => Accepted)
        else
          Effect.reply(replyTo)(Accepted) // removing an item is idempotent

      case AdjustItemQuantity(itemId, quantity, replyTo) =>
        if(items.contains(itemId))
          Effect
            .persist(ItemQuantityAdjusted(itemId, quantity))
            .thenReply(replyTo)(_ => Accepted)
        else
          Effect.reply(replyTo)(Rejected(s"Cannot adjust quantity for item '$itemId'. Item not present on cart"))

      // check it out
      case Checkout(replyTo) =>
        Effect
          .persist(CartCheckedOut)
          .thenReply(replyTo){ updatedCart => // updated cart is state updated after applying CartCheckedOut
            Accepted
          }

      case Get(replyTo) =>
        Effect.reply(replyTo)(ShoppingCartSummary(items, checkedOut = true))
  }
}

case class CheckedOutShoppingCart(items: Map[String, Int]) extends ShoppingCart {
  def applyCommand(cmd: ShoppingCartCommnad) =
    cmd match {
      // CheckedOut is a final state, no mutations allowed
      case AddItem(_, _, replyTo) =>
        Effect.reply(replyTo)(Rejected("Cannot add an item to a checked-out cart"))
      case RemoveItem(_, replyTo) =>
        Effect.reply(replyTo)(Rejected("Cannot remove an item from a checked-out cart"))
      case AdjustItemQuantity(_, _, replyTo) =>
        Effect.reply(replyTo)(Rejected("Cannot adjust an item quantity on a checked-out cart"))
      case Checkout(replyTo) =>
        Effect.reply(replyTo)(Rejected("Cannot checkout a checked-out cart"))

      // it is allowed to read it's state though
      case Get(replyTo) =>
        Effect.reply(replyTo)(ShoppingCartSummary(items, checkedOut = false))
    }

}
```

Command handlers are the meat of the model. They encode the business rules of your model and act as a guardian of the model consistency. The command handler must first validate that the incoming command can be applied to the current model state. In case of successful validation, one or more event expressing the mutations are persisted. Once the events are persisted, they are applied to the state producing a new valid state.

Because an Aggregate is intended to model a consistency boundary, it's not recommended to validate commands using data that's not available in scope. Any decision should be solely based on the data passed in the commands and the state of the aggregate. Any external call should be considered a smell because it means that the aggregate is not in full control of the invariants it's supposed to be protecting.

There are two ways of sending back a reply: using `Effect.reply` and `Effect.persist(...).thenReply`. The first one is available directly on `Effect` and should be used when you reply without persisting any event. In this case, you can use the available state in scope because it's guaranteed to not have changed. The second variant should be used when you have persisted one or more events. The updated state is then made available to you on the function used to define the reply.

You may run side-effects inside the command handler. Please refer to [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html#effects-and-side-effects) for detailed information.

### Defining the Event Handlers

The event handlers are used to mutate the state of the aggregate by applying the events to it. Event handlers must be pure functions as they will be used when instantiating the aggregate and replaying the event journal. Similar to the command handlers, a [recommended style](https://doc.akka.io/docs/akka/2.6/typed/persistence-style.html#command-handlers-in-the-state) is to add them in the state classes.

```scala
sealed trait ShoppingCart  {
  def applyEvent(evt: ShoppingCartEvent): ShoppingCart
}

case class OpenShoppingCart(items: Map[String, Int]) extends ShoppingCart {

  def applyEvent(evt: ShoppingCartEvent): ShoppingCart =
    evt match {
      case ItemAdded(itemId, quantity) => addOrUpdateItem(itemId, quantity)
      case ItemRemoved(itemId) => removeItem(itemId)
      case ItemQuantityAdjusted(itemId, quantity) => addOrUpdateItem(itemId, quantity)
      case CartCheckedOut => CheckedOutShoppingCart(items)
    }

    private def removeItem(itemId: String) = copy(items = items - itemId)
    private def addOrUpdateItem(itemId: String, quantity: Int) =
      copy(items = items + (itemId -> quantity))

}

// CheckedOut is a final state, there can't be any event after it's checked out
case class CheckedOutShoppingCart(items: Map[String, Int]) extends ShoppingCart {
  def applyEvent(evt: ShoppingCartEvent): ShoppingCart = this
}
```

### EventSourcingBehaviour - glueing the bits together

With all the model encoded, the next step is to glue all the pieces together so we can let it run as an Actor. To do that define an `EventSourcedBehavior`. It's recommend to define an `EventSourcedBehavior` using `withEnforcedReplies` when modelling a CQRS Aggregate. Using [enforced replies](https://doc.akka.io/docs/akka/2.6/typed/persistence.html#replies) requires command handlers to return a `ReplyEffect` forcing the developers to be explicit about replies.

```scala
EventSourcedBehavior
  .withEnforcedReplies[ShoppingCartCommand, ShoppingCartEvent, ShoppingCart](
    persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
    emptyState = OpenShoppingCart(Map.empty),
    commandHandler = (cart, cmd) => cart.applyCommand(cmd),
    eventHandler = (cart, evt) => cart.applyEvent(evt)
  )
```

The `EventSourcedBehavior` has four fields to be defined: `persistenceId`, `emptyState`, `commandHandler` and `eventHandler`.

The `persistenceId` defines the id that will be used in the event journal. The id is composed of a name (eg: `entityContext.entityTypeKey.name`) and a business id (eg: `entityContext.entityId`). These two values will be concatenated using a `"|"` by default, eg: `"ShoppingCart|123456"`. See [Akka's documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html#persistenceid) for more details.

> The `entityContext` that appears in scope here will be introduced when covering `ClusterSharding` later in this guide.

The `emptyState` is the state that will be used to when the journal is empty. It's the initial state.

The `commandHandler` is a function `(State, Command) => ReplyEffect[Event, State]`. In this example it's being defined using the `applyCommand` on the passed state. Equally, the `eventHandler` is a function `(State, Event) => Event` and also defined in the passed state.

### Changing behavior - Finite State Machines

If you are familiar with general Akka Actors, you are probably aware that after processing a message you should return the next behavior to be used. With Akka Persistence Typed this happens in a different fashion. Command handlers and event handlers are all dependent on the current state, therefore can you change behavior by returning a new state in the event handler. Consult the [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence.html#changing-behavior) for more insight on this topic.

### Tagging the events - Akka Persistence Query considerations

Events are persisted in the event journal and are primarily used to replay the state of the aggregate each time it needs to be instantiated. However, in CQRS, we also want to consume those same events and generate read-side views or publish them in a message broker (eg: Kafka) for external consumption.

To be able to consume the events on the read-side, the events must be tagged. This is done using the `AggregateEventTag` utility. It's recommended to shard the tags so they can be consumed in a distributed fashion by Lagom's [Read-Side Processor](https://www.lagomframework.com/documentation/current/scala/ReadSide.html) and [Topic Producers](https://www.lagomframework.com/documentation/current/scala/MessageBrokerApi.html#Implementing-a-topic).
Although not recommended, it's also possible to not shard the events as explained [here](https://www.lagomframework.com/documentation/current/scala/ReadSide.html#Event-tags).

This example splits the tags into 10 shards and defines the event tagger in the companion object of `ShoppingCartEvent`. Note that the tag name must be stable as well as the number of shards. These two values can't be changed later without migrating the journal.

```scala
object ShoppingCartEvent {
  val Tag = AggregateEventTag.sharded[ShoppingCartEvent](numShards = 10)
}
```

The `AggregateEventTag` is a Lagom class used by Lagom's [Read-Side Processor](https://www.lagomframework.com/documentation/current/scala/ReadSide.html) and [Topic Producers](https://www.lagomframework.com/documentation/current/scala/MessageBrokerApi.html#Implementing-a-topic), however Akka Persistence Typed expects a function `Event => Set[String]`. Therefore, we need to use an adapter to transform Lagom's `AggregateEventTag` to the required Akka tagger function.

```scala
EventSourcedBehavior
  .withEnforcedReplies[ShoppingCartCommand, ShoppingCartEvent, ShoppingCart](
    persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
    emptyState = OpenShoppingCart(Map.empty),
    commandHandler = (cart, cmd) => cart.applyCommand(cmd),
    eventHandler = (cart, evt) => cart.applyEvent(evt)
  ).withTagger(AkkaTaggerAdapter.fromLagom(entityContext, ShoppingCartEvent.Tag))
```

>  `entityContext` will be introduced on the next section.

### Configuring snapshots

Snapshotting is a common optimization to avoid replaying all the events since the beginning.

You can define snapshot rules in two ways: by predicate and by counter. Both can be combined. The example below uses a counter to illustrate the APIs. You can find more details on the [Akka documentation](https://doc.akka.io/docs/akka/2.6/typed/persistence-snapshot.html).

```scala
EventSourcedBehavior
  .withEnforcedReplies[ShoppingCartCommand, ShoppingCartEvent, ShoppingCart](
    persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
    emptyState = OpenShoppingCart(Map.empty),
    commandHandler = (cart, cmd) => cart.applyCommand(cmd),
    eventHandler = (cart, evt) => cart.applyEvent(evt)
  )
  // snapshot every 100 events and keep at most 2 snapshots on db
  .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2)
```

## Akka Cluster Sharding

Lagom uses [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html) to distribute the Aggregates across all the nodes and guarantee that, at any single time, there is only one instance of a given Aggregate loaded in memory over the whole cluster.

### Creating the Aggregate instance

In order to use the Aggregate, first it needs to be initialized on the `ClusterSharding`. That process won't create any specific Aggregate instance, it will only create the Shard Regions and prepare it to be used (read more about Shard Regions in the [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html) docs).

>  Note: In Akka Cluster, the term to refer to a sharded actor is _entity_ so an Aggregate that's sharded is can also be referred to as an Aggregate Entity.

In the companion object of `ShoppingCart`, define an `EntityTypeKey` and factory method to initialize the `EventSourcedBehavior` for the Shopping Cart Aggregate. The `EntityTypeKey`  has as name to uniquely identify this model in the cluster. It's also typed on `ShoppingCartCommand` which is the type of the messages that the Aggregate can receive.

Then, you must also define a function of `EntityContext[Command] => Behavior[Command]`. This can also be defined as a method in the companion object.

```scala
object ShoppingCart {

  val typeKey = EntityTypeKey[ShoppingCartCommand]("ShoppingCart")

  def behavior(entityContext: EntityContext[ShoppingCartCommand]): Behavior[ShoppingCartCommand] = {
    EventSourcedBehavior
      .withEnforcedReplies[ShoppingCartCommand, ShoppingCartEvent, ShoppingCart](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = OpenShoppingCart(Map.empty),
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, ShoppingCartEvent.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2)
  }
}
```

Finally, initialize the Aggregate on the `ClusterSharding` using the `typedKey` and the `behavior`. Lagom provides an instance of the `clusterSharding` extension through dependency injection for you convenience. Initializing an entity should be done only once and, in the case of Lagom Aggregates it is tipically done in the `LagomApplication`:

```scala
abstract class ShoppingCartApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with SlickPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {

  override lazy val lagomServer = serverFor[ShoppingCartService](wire[ShoppingCartServiceImpl])
  override lazy val jsonSerializerRegistry = ShoppingCartSerializerRegistry

  clusterSharding.init(
    Entity(ShoppingCartState.typeKey) {
      ctx => ShoppingCartState.behavior(ctx)
    }
  )
}
```

### Getting instances of the Aggregate Entity

To access instances of the Aggregate (which may be running locally or remotely on the cluster), you should inject the `ClusterSharding` on your service can instantiate an `EntityRef` using the method `entityRefFor`. 

```scala
val shoppingCartRef: EntityRef[ShoppingCartCommand] =
    clusterSharding.entityRefFor(ShoppingCart.typeKey, "abc-123")
```

To locate the correct actor across the cluster you need to specify the `entityTypeKey` we used to initialize the entity and the `id` for the instance we need. Akka Cluster will create the required actor in one node on the cluster or reuse the existing instance if the actor has already been created and is still alive.

The `entityRef` is similar to an `actorRef` but denotes the actor is sharded. Interacting with an `entityRef` implies the messages exchanged with the actor may need to travel over the wire to a separate node. In our case, the `EntityRef` is typed to only accept `ShoppingCartCommand`s.

#### Considerations on using ask pattern

Since we want to send commands to the Aggregate and these commands declare a replay we will need to use the `ask` pattern.

The code we introduced above creates an `EntityRef` from the `ShoppingCartServiceImpl`. This means it is code outside an actor (the `ServiceImpl`) trying to interact with an actor (the `EntityRef`). `EntityRef` provides an `ask()` overload out of the box meant to be used from outside actors which is the situation we're in.

```scala
implicit val askTimeout = Timeout(5.seconds)

val futureSummary: Future[ShoppingCartSummary] =
   shoppingCartRef
      .ask(replyTo => Get(replyTo))
futureSummary.map(cartSummary => convertToApi(id, cartSummary))
```

So we declare an implicit `timeout` and then invoke `ask(f)` (which uses the timeout implicitly). The `f` argument in the `ask()` method is a function in which we create the command using the `replyTo` provided. Internally, Akka Typed will use that `replyTo` as a receiver of the response message and then extract that message and provide it as as `Future[Reply]` (in this case `Future[ShoppingCartSummary]`).

Finally, we operate over the `futureSummary`normallly (in this case, we map it to a different type).

### Configuring number of shards

As detailed in the [Akka Cluster Sharding docs](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html#shard-allocation):

> As a rule of thumb, the number of shards should be a factor ten greater  than the planned maximum number of cluster nodes. It doesn’t have to be  exact. Fewer shards than number of nodes will result in that some nodes  will not host any shards. Too many shards will result in less efficient  management of the shards

See the Akka docs for details on how to configure the number of shards.

### Configuring Entity passivation

Keeping all the Aggregates in memory all the time is inefficient. Instead, use the Entity passivation feature so sharded entities (the Aggregates) are removed from the cluster when they've been unused for some time.

Akka supports both programmatic passivation and [automatic passivation](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html#automatic-passivation). The default values for automatic passivation are generally good enough.

## Data Serialization

The messages (commands, replies) and the durable classes (events, state snapshots) need to be serializable to be sent over the wire across the cluster or be stored on the database. Akka recommends [Jackson-based serializers](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html) --preferably JSON but CBOR is also supported-- as a good default in most cases. On top of Akka serializers, Lagom makes it easy to add [[Play-JSON serialization|SerializationPlayJson]] support which may be more familiar to some scala developers.

In Akka Persistence Typed, in Akka Persistence Typed, in particular, and when you adopt CQRS/ES practices, commands will include a `replyTo: ActorRef[Reply]` field. This `replyTo` field will be used on your code to send a `Reply` back as shown in the examples above. Serializing an `ActorRef[T]` requires using the Akka Jackson serializer.

The limitation to use Akka Jackson for Command messages doesn't apply to other messages like events, snapshots, or even replies. Each type Akka needs to serialize may use a different serializer.

Read more about the serialization setup and configuration in the [[serialization|Serialization]] section.