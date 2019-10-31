# Migrating to Akka Persistence Typed


## Migrating the model
  * failing a command ?
  *
### Commands
  * cross-link to serialization section below
### Replies
  * introduce `Confirmation` 
### State
  * ??? 

## Registration
  * There's no longer a Persistent Entity to register but the Registry is still necessary!
  * injecting clusterSharding
    * EntityTypeKey
    * behavior
## Keeping it compatible 
### Existing Journal data
### Read-Side processors


## Serialization

All the classes sent over the wire or stored on the database will still need to be serializable. Persisted events will still have to be read. 

Existing code creating and registering serializers is 100% valid except for `Command` classes. In Akka Typed, it is required to add a `replyTo: ActorRef[Reply]` field on messages that need a rference to reply back (like `Commands`). In order to serialize a class that includes an `ActorRef[T]` field the class must use the Akka Jackson serializer. Read more on the [[serialization|Serialization]] section of the docs.

To convert your `Command` classes to use Akka Jackson serialization instead of Lagom Play-JSON you need to follow these steps:

First,create a marker trait. For example:
 
@[akka-jackson-serialization-marker-trait](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

Then, use the regular Akka serialization binding mechanism so all classes extending that trait use the Akka Jackson JSON serializer:

@[akka-jackson-serialization-binding](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/resources/application.conf)

Then, remove all code that's `play-json` from your Command classes and companion objects. _Before_:

@[akka-jackson-serialization-command-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-lagom-persistence/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

_After_:

@[akka-jackson-serialization-command-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

Note how the type of the reply is no longer specified via `ReplyType[T]` but as the type of the protocol the `replyTo: ActorRef[T]` actor.

And finally, remove all commands from JsonSerialiserRegistry

@[akka-jackson-serialization-registry-before](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-lagom-persistence/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)

@[akka-jackson-serialization-registry-after](../../../../../dev/sbt-plugin/src/sbt-test/sbt-plugin/akka-persistence-typed-migration-scala/shopping-cart-akka-persistence-typed/src/main/scala/com/example/shoppingcart/impl/ShoppingCartEntity.scala)
