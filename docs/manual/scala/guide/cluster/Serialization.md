# Serialization

Persisteng events and exchanging messages between the nodes of the cluster requires serialization. Lagom uses Akka serialization mechanisms to bind serializers for each of the message sent over the wire. Akka recommends [Jackson-based serializers](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html) --preferably JSON but CBOR is also supported-- as a good default in most cases. On top of Akka serializers, Lagom makes it easy to add [[Play-JSON serialization|SerializationPlayJson]] support which may be more familiar to some scala developers.

Runtime overhead is avoided by not basing the serialization on reflection. Transformations to and from JSON are defined either manually or by using a built in macro - essentially doing what reflection would do, but at compile time instead of during runtime. This comes with one caveat, each top level class that can be serialized needs an explicit serializer defined.

The Akka documentation for [Serialization with Jackson](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html) already covers basic concepts like [Usage](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html#usage) (setting up the bindings and creating a marking trait), [Schema Evolution](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html#schema-evolution), and even additional configuration and features.

The Play JSON abstraction for serializing and deserializing a class into JSON is the [Format](https://www.playframework.com/documentation/2.6.x/api/scala/index.html#play.api.libs.json.Format) which in turn is a combination of [Reads](https://www.playframework.com/documentation/2.6.x/api/scala/index.html#play.api.libs.json.Reads) and [Writes](https://www.playframework.com/documentation/2.6.x/api/scala/index.html#play.api.libs.json.Writes). The library parses JSON into a JSON tree model, which is what the `Format`s work with.

> You can opt out of Lagom JSON and Akka Jackson serializers and bind your classes to other serializers (e.g. protobuf). This documentation doesn't cover the necessary steps to opt-out. See [Serialization](https://doc.akka.io/docs/akka/2.6/serialization.html) on the Akka docs for details.

## Serialization limitations in Akka Typed

In Akka Typed, messages often include a `replyTo: ActorRef[T]` field so the actor handling the message can send a message back. Serializing an `ActorRef[T]` requires using the Akka Jackson serializer. If you use Akka Typed Persistence in Lagom, you will have to use Akka JAckson to serialize your commands because command messages sent to an Aggregate include a `replyTo: ActorRef[MyReply]` field. 

The limitation to use Akka Jackson for Command messages doesn't apply to other messages like events, or even replies. Each type Akka needs to serialize may use a different serializer.

# Intro to Akka Jackson

Following is a short summary of hot to use the Akka Jackson serialiser in Lagom. For further details, refer to the [Serialization with Jackson](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html) docs.

## Using Akka Jackson Serialization in Lagom

Setting up Akka Jackson serialization requires three steps.

First, create a custom marker trait, for example `trait CommandSerializable`.

Then, make suer all classes that must use Akka Jackson's serializer extend that trait:

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

When you persiste durable events on an Akka Persistence Journal, events created in eraly iteration will still have to be readable in future versions of your application. As you evolve your schema you will have to create and register data migration so that old events are upgraded in-flight when read from the Journal. Read the Akka docs on [Schema Evolution](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html#schema-evolution) for details.
