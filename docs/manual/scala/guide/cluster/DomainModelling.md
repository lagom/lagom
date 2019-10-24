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
final case class ShoppingCartSummary(items: Map[String, Int], checkedout: Boolean)

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

final case class Get(replyTo: ActorRef[ShoppingCartSummary])
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
        if (quantity > 0)
          Effect
            .persist(ItemAdded(itemId, quantity))
            .thenReply(replyTo) { updatedCart => // updatedCart is the state updated after applying ItemUpdated
              Accepted
            }
        else
          Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))

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
            .persist(ItemQuantityAdjusted(itemId))
            .thenReply(replyTo)(_ => Accepted)
        else
          Effect.reply(replyTo)(Rejected(s"Cannot adjust quantity for item '$itemId'. Item not present on cart"))

      // check it out
      case Checkout(replyTo) =>
        Effect
          .persist(CheckedOut)
          .thenReply(replyTo){ updatedCart => // updated cart is state updated after applying CheckedOut
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
      case CheckedOut => CheckedOutShoppingCart(items)
    }

    private def removeItem(itemId: String) = copy(items = items - itemId)
    private def addOrUpdateItem(itemId: String, quantity: Int) =
      copy(items = items + (itemId -> quantity))

}

// CheckedOut is a final state, there can't be any event after its checked out
case class CheckedOutShoppingCart(items: Map[String, Int]) extends ShoppingCart {
  def applyEvent(evt: ShoppingCartEvent): ShoppingCart = this
}
```

### EventSourcingBehaviour - glueing the bits together

TODO: explain `withExpectingReplies`

### Changing behavior - Finite State Machines

### Tagging the events - Akka Persistence Query considerations

// TODO explain tagging
```scala
object ShoppingCartEvent {
  val Tag = AggregateEventTag.sharded[ShoppingCartEvent](numShards = 10)
}
```

### Configuring snaptshots

## ClusterSharding

### Initialize the Entity - register Behavior on ClusterSharding

### How to use an Entity

#### looking up an instance in ClusterSharding

#### considerations on using ask pattern

### configuring number of shards

### configuring Entity passivation

## Data Serialization
