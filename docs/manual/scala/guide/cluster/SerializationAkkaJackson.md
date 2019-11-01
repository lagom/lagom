# Intro to Akka Jackson

Following is a short summary of how to use the Akka Jackson serialiser in Lagom. For further details, refer to the [Serialization with Jackson](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html) docs.

## Using Akka Jackson Serialization in Lagom

Setting up Akka Jackson serialization requires three steps.

First, create a custom marker trait, for example `trait CommandSerializable`.

Then, make sure all classes that must use Akka Jackson's serializer extend that trait:

```scala
package com.example.shoppingcart.impl

object ShoppingCart {
  trait CommandSerializable
  sealed trait Command extends CommandSerializable
  final case class AddItem(itemId: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends Command
  final case class RemoveItem(itemId: String, replyTo: ActorRef[Confirmation]) extends Command
  // More command classes
}
```

Finally, register the binding of your marker trait to Akka Jackson JSON serializer in `application.conf`:

```
akka.actor {
  serialization-bindings {
    # Commands won't use play-json but Akka's jackson support.
    # See https://doc.akka.io/docs/akka/2.6/serialization-jackson.html
    "com.example.shoppingcart.impl.ShoppingCart$CommandSerializable" = jackson-json
  }
} 
```

## Schema Evolution

When you persist durable events on an Akka Persistence Journal, events created in early iterations of your software will still have to be readable in future versions of your application. As you evolve your schema you will have to create and register data migration code so that old events are upgraded in-flight when read from the Journal in the database. Read the Akka docs on [Schema Evolution](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html#schema-evolution) for details.
