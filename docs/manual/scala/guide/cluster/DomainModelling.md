# Domain Modelling

This section presents all the steps to model an [Aggregate](https://martinfowler.com/bliki/DDD_Aggregate.html), as defined in Domain-Driven Design, using [Akka Persistence Typed](https://doc.akka.io/docs/akka/current/typed/persistence.html) and following the [[CQRS|ES_CQRS]] principles embraced by Lagom. While Akka Persistence Typed provides an API for building event-sourced actors, the same does not necessarily apply for CQRS Aggregates. To build CQRS applications, we need to applying a few rules to our design.

A simplified shopping cart example is used to guide you through the process. You can find a full-fledge shopping cart sample on our [samples repository](https://github.com/lagom/lagom-samples/tree/1.6.x/shopping-cart/shopping-cart-scala).

## Encoding the model

Start by defining your model in terms of Commands, Events, and State.

The state of the shopping cart is defined as following:

### Modelling the State

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

Each command has a `replyTo:ActorRef[R]` field where `R` is the reply that will be sent back to the caller. Replies are used to communicate back if a command was accepted or rejected or to read the aggregate data (ie: read-only commands). It's also possible to have a mix of both, for example, if the command succeeds it returns some updated data, if it fails it returns a rejected message. It's also possible to have commands without replies (ie: fire-and-forget). This is a less common pattern in CQRS Aggregate modelling though and not covered in this example.

In Akka Typed, it's not possible to return an exception to the caller. All communication between the actor and the caller must be done via the `replyTo:ActorRef[R]` passed in the command. Therefore, if you want to signal a rejection, you most have it encoded in your reply protocol.

```scala
// Replies
sealed trait Confirmation
sealed trait Accepted extends Confirmation
case object Accepted extends Accepted
final case class Rejected(reason: String) extends Confirmation
final case class CartState(items: Map[String, Int], status: String)

// Commands
sealed trait ShoppingCartCommand
final case class UpdateItem(productId: String,
                            quantity: Int,
                            replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class Checkout(replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class Get(replyTo: ActorRef[CartState])
```

Note that we have different kinds of replies: `Confirmation` used when we want to modify the state. A modification request can be `Accepted` or `Rejected`. And `CartState` used when we want to read the state of the shopping cart. `CartState` is not the shopping cart itself, but the representation we want to expose to the external world.

### Modelling Events

Next we define the events that our model will be persisting. The events must extend Lagom's `AggregateEvent`. This is import for tagging the events. A topic we will cover a little further.

```scala
sealed trait ShoppingCartEvent extends AggregateEvent[ShoppingCartEvent] {
  def aggregateTag = ShoppingCartEvent.Tag
}

final case class ItemUpdated(productId: String, quantity: Int)
  extends ShoppingCartEvent

final case object CheckedOut extends ShoppingCartEvent
```

### Defining Commands Handlers

Once you define your protocol in terms of Commands, Replies, Events and State, you need to define the buiseness rules of your model. The command handlers define how to handle each incoming command, which validations must be applied and finally which events will be persisted, if any.

You can encoding it in different ways. One popular style is to add the command handlers in your model classes. Since `ShoppingCart` have two state classes extensions, it makes sense to add the repective business rules validation on each state class. Each possible state will define how each command should be handled.

```scala
sealed trait ShoppingCart  {
  def applyCommand(cmd: ShoppingCartCommnad): ReplyEffect[ShoppingCartEvent, ShoppingCartState]
}

case class OpenShoppingCart(items: Map[String, Int]) extends ShoppingCart {
  def applyCommand(cmd: ShoppingCartCommnad) =
    cmd match {
      case UpdateItem(_, qty, replyTo) if qty < 0 =>
        Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))

      // an item is delete by setting it's quantity to 00
      case UpdateItem(productId, 0, replyTo) if !items.contains(productId) =>
        Effect.reply(replyTo)(Rejected("Cannot delete item that is not already in cart"))

      case UpdateItem(productId, quantity, replyTo) =>
        Effect
          .persist(ItemUpdated(productId, quantity))
          .thenReply(replyTo) { updatedCart => // updated cart is state updated after applying ItemUpdated
            Accepted
          }

      // check it out
      case Checkout(replyTo) =>
        Effect
          .persist(CheckedOut)
          .thenReply(replyTo){ updatedCart => // updated cart is state updated after applying CheckedOut
            Accepted
          }

      case Get(replyTo) =>
        Effect.reply(replyTo)(CartState(items, status = "open"))
  }
}

case class CheckedOutShoppingCart(items: Map[String, Int]) extends ShoppingCart {
  def applyCommand(cmd: ShoppingCartCommnad) =
    cmd match {
      // CheckedOut is a final state, not mutations allowed
      case UpdateItem(_, _, replyTo) =>
        Effect.reply(replyTo)(Rejected("Cannot update a checked-out cart"))
      // CheckedOut is a final state, not mutations allowed
      case Checkout(replyTo) =>
        Effect.reply(replyTo)(Rejected("Cannot checkout a checked-out cart"))

      // it is allowed to read it's state though
      case Get(replyTo) =>
        Effect
        .reply(replyTo)(CartState(items, status = "checked-out"))
    }

}
```

Command handlers are the meat of the model. They encode the business rules of your model and act as a guardian of the model consistency. Any mutation must be validated by it. However, they don't apply the mutations. Instead, they express the mutations in the form of persisted events.

Because an Aggregate is intended to model a consistency boundary, it's not recommended to validate commands using data that's not available in scope. Any decision should be solely based on the data passed in the commands and the state of the aggregate. Any external call should be considered a smell because it means that the aggregate is not in full control of the invariants it's supposed to be the guardian.

You may have notice that there two ways of sending back a reply. Using `Effect.reply` and `Effect.persist(...).thenReply`. The first one is available directly on `Effect` and should be used when you reply without persisting any event. In this case, you can use the available state in scope because it's guaranteed to not have changed. The second variant should be used when you have persisted one or more events. The updated state is then made available to you on the function used to define the reply.

You may run side-effects inside the command handler. Akka Persistence Typed offers an API for it. However, be aware that any side-effect has at-most-once semantic. They are run after the events are persisted. In the face of a crash, it may happen that your events are persisted, but the side-effects are not executed. If that happens, they won't be retried. You define side-effect using the `Effect.persist(...).thenRun(callback: State => Unit)` method on the `Effect`.

```scala
Effect
  .persist(ItemUpdated(productId, quantity))
  // side-effect: run after persisting. Has at-most-once semantics
  .thenRun(updatedCart => println(updatedCart))
  .thenReply(replyTo)(_ => Accepted)
```

### Defining the Event Handlers

The event handlers are used to mutate the state of the aggregate by applying the events to it. Event handlers must be pure functions as they will be used when instantiating the aggregate and replying the event journal.

```scala
sealed trait ShoppingCart  {
  def applyEvent(evt: ShoppingCartEvent): ShoppingCartState
}

case class OpenShoppingCart(items: Map[String, Int]) extends ShoppingCart {

  def applyEvent(evt: ShoppingCartEvent): ShoppingCartState =
    evt match {
      case ItemUpdated(productId, quantity) => updateItem(productId, quantity)
      case CheckedOut => CheckedOutShoppingCart(items)
    }

  private def updateItem(productId: String, quantity: Int) =
    quantity match {
      case 0 => copy(items = items - productId)
      case _ => copy(items = items + (productId -> quantity))
    }

}

// CheckedOut is a final state, there can be any event after its checked out
case class CheckedOutShoppingCart(items: Map[String, Int]) extends ShoppingCart {
  def applyEvent(evt: ShoppingCartEvent): ShoppingCartState = this
}
```

### EventSourcingBehaviour - glueing the bits together

TODO: explain `withExpectingReplies`

### Changing behavior - Finite State Machines

### EventSourcingBehaviour - glueing the bits together

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
