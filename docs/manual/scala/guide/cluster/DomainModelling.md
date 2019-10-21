# Domain Modelling

We recommend reading [[Event Sourcing and CQRS|ES_CQRS]] as a prerequisite to this section.

This section presents all the steps to model an [Aggregate](https://martinfowler.com/bliki/DDD_Aggregate.html), as defined in Domain-Driven Design, using [Akka Persistence Typed](https://doc.akka.io/docs/akka/current/typed/persistence.html) and following the [[CQRS|ES_CQRS]] principles embraced by Lagom. While Akka Persistence Typed provides an API for building event-sourced actors, the same does not necessarily apply for CQRS Aggregates. To build CQRS applications, we need to use a few rules in our design.

Lagom embraces Event Sourcing and CQRS by promoting the usage of Akka Persistence Typed in combination the CQRS principle. Akka Persistence Typed provides an API for building event sourced actors, but not necessarily for CQRS Aggregates. To build CQRS applications we need to apply a few rules to our design.

This section explains the API and concepts while building a simplified shopping cart model. You can find a full-fledge shopping cart sample on our [samples repository](https://github.com/lagom/lagom-samples/tree/1.6.x/shopping-cart/shopping-cart-scala).

You will learn, how to:

* model a CQRS Aggregate using Akka Persistence Typed
* prepare its event journal to be queryable using event tags
* register the model on Akka's Cluster Sharding
* look up instances on the cluster and interact with them
* define serializers for your commands, events and model state
* understand the main configuration settings and their raison-être

## Encoding the model

 We will start by defining the rules of our shopping cart.

* a shopping cart starts empty, gets items added to it and can be either cancelled or checkedout
* an empty shopping cart can't be checkedout
* a cancelled or checkedout can't have new items added to it
* items are simplified to a `name` and a `quantity`
* when you re-add an item, you override the exisitng by change its quantity
* you delete an item by updating its quantity to 0

### Typed protocol - Command, Event and State

We will start by define our model in terms of Commands, Events and State.

The state of the shopping cart will be defined as following:

```scala
/** Common Shopping Cart trait */
sealed trait ShoppingCart
/** A shopping cart starts as empty */
case object EmptyShoppingCart extends ShoppingCart
/** Once we add new items it becomes an open shopping cart */
case class OpenShoppingCart(items: Map[String, Int]) extends ShoppingCart
/** Once cancelled it reaches end-of-life and can't be used anymore */
case object CancelledShoppingCart extends ShoppingCart
/** Once checked-out it reaches end-of-life and can't be used anymore */
case object CheckedOutShoppingCart extends ShoppingCart
```

Next we define the commands that we can send to it. Each command has a `replyTo:ActorRef[R]` field where `R` is the reply that will be sent back to the caller. We will cover it in detail in a while.

```scala
// Replies
sealed trait Confirmation
sealed trait Accepted extends Confirmation
case object Accepted extends Accepted
final case class Rejected(reason: String) extends Confirmation
final case class CurrentState(items: Map[String, Int]) extends ShoppingCartReply

// Commands
sealed trait ShoppingCartCommand
final case class UpdateItem(productId: String,
                            quantity: Int,
                            replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class Checkout(replyTo: ActorRef[Confirmation])
  extends ShoppingCartCommand

final case class Cancel(replyTo: ActorRef[Accepted])
  extends ShoppingCartCommand

final case class Get(replyTo: ActorRef[CurrentState])
```

> Note that we have different kinds of replies: `Confirmation` used when we want to modify the state. A modification request can be `Accepted` or `Rejected`. And `CurrentState` used when we want to read the current state.

Next we define the events that our model will be persisting. The events must extend Lagom's `AggregateEvent`. This is import for tagging the events. A topic we will cover a little further.

```scala
sealed trait ShoppingCartEvent extends AggregateEvent[ShoppingCartEvent] {
  def aggregateTag = ShoppingCartEvent.Tag
}

final case class ItemUpdated(productId: String, quantity: Int)
  extends ShoppingCartEvent

final case object CheckedOut extends ShoppingCartEvent

final case object Cancelled extends ShoppingCartEvent
```

### Typed replies and error handling

### Enforced replies

### Changing behavior - Finite State Machines

## EventSourcingBehaviour - glueing the bits together

### Tagging the events - Akka Persistence Query considerations

// TODO explain tagging
```scala
object ShoppingCartEvent {
  val Tag = AggregateEventTag.sharded[ShoppingCartEvent](numShards = 10)
}
```

* Configuring snaptshots

## ClusterSharding

* initialize it - register Behavior on ClusterSharding
* how to use an Entity
  * looking up an instance in ClusterSharding
  * considerations on using ask pattern
* configuring number of shards
* configuring Entity passivation

## Data Serialization
