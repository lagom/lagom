# Service Descriptors

Lagom services are described by an interface, known as a service descriptor.  This interface not only defines how the service is invoked and implemented, it also defines the metadata that describes how the interface is mapped down onto an underlying transport protocol.  Generally, the service descriptor, its implementation and consumption should remain agnostic to what transport is being used, whether that's REST, websockets, or some other transport.  Let's take a look at a simple descriptor:

@[hello-service](code/docs/services/HelloService.java)

This descriptor defines a service with one call, the `sayHello` call. `sayHello()` is a method that returns something of type `ServiceCall`, this is a representation of the call that can be invoked when consuming the service, and implemented by the service itself.  This is what the interface looks like:

```java
interface ServiceCall<Id, Request, Response> {
  CompletionStage<Response> invoke(Id id, Request request);
}
```

An important thing to note here is that invoking the `sayHello()` method does not actually invoke the call, it simply gets a handle to the call, which can then be invoked using the `invoke` method.

[`ServiceCall`](api/java/index.html?com/lightbend/lagom/javadsl/api/ServiceCall.html) takes three type parameters, `Id`, `Request` and `Response`.  The `Id` is extracted from the incoming identifier - usually the path in the case of a REST request - of the call.  The example above doesn't have an ID, which means when implemented using the REST transport it's going to use a static path.  The `Request` parameter is the type of the incoming request message, and the `Response` parameter is the type of the outgoing response message.  In the example above, these are both `String`, so our service call just handles simple text messages.

While the `sayHello()` method describes how the call will be programmatically invoked or implemented, it does not describe how this call gets mapped down onto the transport.  This is done by providing a `default` implementation of the [`descriptor()`](api/java/index.html?com/lightbend/lagom/javadsl/api/Service.html#descriptor--) call, whose interface is described by [`Service`](api/java/index.html?com/lightbend/lagom/javadsl/api/Service.html).

You can see that we're returning a service named `hello`, and we're describing one call, the `sayHello` call.  Because this service is so simple, in this case we don't need to do anything more than simply pass the call to the [`call`](api/java/index.html?com/lightbend/lagom/javadsl/api/Service.html#call-com.lightbend.lagom.javadsl.api.ServiceCall-) method.  When mapped to a REST transport, Lagom will map `sayHello()` calls to a `POST` request on a static path of `/sayHello`, with `text/plain` request and response bodies.  All of this is configurable, as we'll see below.

## Call identifiers

Each service call needs to have an identifier.  An identifier is used to provide routing information to the implementation of the client and the service, so that calls over the wire can be mapped to the appropriate call.  Identifiers can be a static name or path, or they can have dynamic components, where a dynamic id is extracted from a path.  The dynamic id type is represented in the `ServiceCall` interface using the `Id` type parameter, when the call identifier is static, this type parameter will be `akka.NotUsed`.


The simplest type of identifier is a name, and by default, that name is set to be the same name as the name of the method on the interface that implements it.  A custom name can also be supplied, by passing it to the [`namedCall`]((api/java/index.html?com/lightbend/lagom/javadsl/api/Service.html#namedCall-java.lang.String-com.lightbend.lagom.javadsl.api.ServiceCall-)) method:

@[call-id-name](code/docs/services/FirstDescriptor.java)

In this case, we've named it `hello`, instead of the default of `sayHello`.  When implemented using REST, this will mean this call will have a path of `/hello`.

### Path based identifiers

The second type of identifier is a path based identifier.  This uses a URI path and query string to route calls, and from it a dynamic identifier can optionally be extracted out.  They can be configured using the [`pathCall`](api/java/index.html?com/lightbend/lagom/javadsl/api/Service.html#pathCall-java.lang.String-com.lightbend.lagom.javadsl.api.ServiceCall-) method.

Dynamic ids are extracted from the path using an [`IdSerializer`](api/java/index.html?com/lightbend/lagom/javadsl/api/deser/IdSerializer.html).  Lagom provides many id serializers out of the box, these can be found in [`IdSerializers`](api/java/index.html?com/lightbend/lagom/javadsl/api/deser/IdSerializers.html), and if your `Id` type is one of those, Lagom will be able to automatically use that.  For example, here's an example call that uses a `Long` id:

@[call-long-id](code/docs/services/FirstDescriptor.java)

When your id is more complex, such as when it has multiple path components, you will need to define a custom type to represent it.  Lagom id types should be very simple immutable objects.  Lagom provides some helpers for creating serializers for id types, the most useful being the `create` methods.  These helpers are designed to take a method reference, such as the constructor of the type, and then a lambda to extract the same parameters back out into a list.  The custom id serializer can then be passed to the `with` method on the service call.  For example, here's an `ItemId`, that contains both an `orderId` property and an `id` property:

@[item-id](code/docs/services/simpleitemid/AbstractItemId.java)

Note that we're using the [[Immutables|Immutable#Immutables]] library here, so this will generate an immutable `ItemId` class.  We're also using the `@Value.Parameter` annotation to tell it to generate a static constructor method called `of`.  Now, using that static constructor method, we can create an id serializer:

@[call-simple-item-id](code/docs/services/simpleitemid/Descriptors.java)

You may have many routes that use the same id, in which case, you can configure the custom id serializer at the service level, like so:

@[call-service-item-id](code/docs/services/simpleitemid/Descriptors.java)

Id serializers can also work with types produced by other id serializers, for example, let's say you had the following `OrderId` class:

@[order-id](code/docs/services/AbstractOrderId.java)

And then an `ItemId` class that used it:

@[item-id](code/docs/services/AbstractItemId.java)

Then if you define id serializers for those at the service level, Lagom will wire them together for you:

@[call-complex-item-id](code/docs/services/FirstDescriptor.java)

### REST identifiers

The final type of identifier is a REST identifier.  REST identifiers are designed to be used when creating semantic REST APIs.  They use both a path, as with the path based identifier, and a request method, to identify them.  They can be configured using the [`restCall`](api/java/index.html?com/lightbend/lagom/javadsl/api/Service.html#restCall-com.lightbend.lagom.javadsl.api.transport.Method-java.lang.String-com.lightbend.lagom.javadsl.api.ServiceCall-) method:

@[call-rest](code/docs/services/FirstDescriptor.java)

## Messages

Every service call in Lagom has a request message type and a response message type.  Like ids, when these are not used, the `akka.NotUsed` can be used in their place.  Request and response message types fall into two categories, strict and streamed.

### Strict messages

A strict message is a single message that can be represented by a simple Java object.  The message will be buffered into memory, and then parsed, for example, as json.  When both message types are strict, the call is said to be a synchronous call, that is, a request is sent and received, then a response is sent and received.  The caller and callee have synchronized in their communication.

So far, all of the service call examples we've seen have used strict messages, for example, the order service descriptors above accept and return items and orders.  The input value is passed directly to the service call, and returned directly from the service call, and these values are serialized to a JSON buffer in memory before being sent, and read entirely into memory before being deserialized back from JSON.

### Streamed messages

A streamed message is a message of type [`Source`](http://doc.akka.io/japi/akka/2.4.4/akka/stream/javadsl/Source.html).  `Source` is an [Akka streams](http://doc.akka.io/docs/akka/2.4.4/java.html) API that allows asynchronous streaming and handling of messages.  Here's an example streamed service call:

@[call-stream](code/docs/services/FirstDescriptor.java)

This service call has a strict request type and a streamed response type.  An implementation of this might return a `Source` that sends the input tick message `String` at the specified interval.

A bidirectional streamed call might look like this:

@[hello-stream](code/docs/services/FirstDescriptor.java)

In this case, the server might return a `Source` that converts every message received in the request stream to messages prefixed with `Hello`.

Lagom will choose an appropriate transport for the stream, typically, this will be WebSockets.  WebSockets support bidirectional streaming, and so are a good general purpose option for streaming.  When only one of the request or response message is streamed, Lagom will implement the sending and receiving of the strict message by sending or receiving a single message, and then leaving the WebSocket open until the other direction closes.  Otherwise, Lagom will close the WebSocket when either direction closes.

### Message serialization

By default, Lagom will choose an appropriate serializer for request and response serialization and deserialization.  Out of the box, Lagom will use JSON for communication, using Jackson to serialize and deserialize messages.

For details on message serializers, including how to write and configure custom message serializers, see [[Message Serializers|MessageSerializers]].
