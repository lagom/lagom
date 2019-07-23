# Service Descriptors

Lagom services are described by an interface, known as a service descriptor.  This interface not only defines how the service is invoked and implemented, it also defines the metadata that describes how the interface is mapped down onto an underlying transport protocol.  Generally, the service descriptor, its implementation and consumption should remain agnostic to what transport is being used, whether that's REST, websockets, or some other transport.  Let's take a look at a simple descriptor:

@[hello-service](code/ServiceDescriptors.scala)

This descriptor defines a service with one call, the `sayHello` call. `sayHello` is a method that returns something of type [`ServiceCall`](api/com/lightbend/lagom/scaladsl/api/ServiceCall.html), this is a representation of the call that can be invoked when consuming the service, and implemented by the service itself.  This is what the interface looks like:

@[service-call](code/ServiceDescriptors.scala)

An important thing to note here is that invoking the `sayHello` method does not actually invoke the call, it simply gets a handle to the call, which can then be invoked using the `invoke` method.

[`ServiceCall`](api/com/lightbend/lagom/scaladsl/api/ServiceCall.html) takes two type parameters, `Request` and `Response`.  The `Request` parameter is the type of the incoming request message, and the `Response` parameter is the type of the outgoing response message.  In the example above, these are both `String`, so our service call just handles simple text messages.

While the `sayHello` method describes how the call will be programmatically invoked or implemented, it does not describe how this call gets mapped down onto the transport.  This is done by providing an implementation of the [`descriptor`](api/com/lightbend/lagom/scaladsl/api/Service.html#descriptor:Descriptor) call, whose interface is described by [`Service`](api/com/lightbend/lagom/scaladsl/api/Service.html).

You can see that we're returning a service named `hello`, and we're describing one call, the `sayHello` call.  Because this service is so simple, in this case we don't need to do anything more than simply passing the Service call `sayHello` defined above in the example as a method reference to the [`call`](api/com/lightbend/lagom/scaladsl/api/Service$.html#call[Request,Response]\(ScalaMethodServiceCall[Request,Response]\)\(MessageSerializer[Request,_],MessageSerializer[Response,_]\):Call[Request,Response]) method.

## Call identifiers

Each service call needs to have an identifier.  An identifier is used to provide routing information to the implementation of the client and the service, so that calls over the wire can be mapped to the appropriate call.  Identifiers can be a static name or path, or they can have dynamic components, where dynamic path parameters are extracted from the path and passed to the service call methods.

The simplest type of identifier is a name, and by default, that name is set to be the same name as the name of the method on the interface that implements it. In the example above, we've used the `call` method to create a service call with a name of `sayHello`. A custom name can also be supplied, by using the [`namedCall`](api/com/lightbend/lagom/scaladsl/api/Service$.html#namedCall[Request,Response]\(String,ScalaMethodServiceCall[Request,Response]\)\(MessageSerializer[Request,_],MessageSerializer[Response,_]\):Call[Request,Response]) method:

@[call-id-name](code/ServiceDescriptors.scala)

In this case, we've named it `hello`, instead of the default of `sayHello`.  When implemented using REST, this will mean this call will have a path of `/hello`.

### Path based identifiers

The second type of identifier is a path based identifier.  This uses a URI path and query string to route calls, and from it dynamic path parameters can optionally be extracted out.  They can be configured using the [`pathCall`](api/com/lightbend/lagom/scaladsl/api/Service$.html#pathCall[Request,Response]\(String,ScalaMethodServiceCall[Request,Response]\)\(MessageSerializer[Request,_],MessageSerializer[Response,_]\):Call[Request,Response]) method.

Dynamic path parameters are extracted from the path by declaring dynamic parts in the path.  These are prefixed with a colon, for example, a path of `/order/:id` has a dynamic part called `id`. Lagom will extract this parameter from the path, and pass it to the service call method. In order to convert it to the type accepted by the method, Lagom will use an implicitly provided [`PathParamSerializer`](api/com/lightbend/lagom/scaladsl/api/deser/PathParamSerializer.html).  Lagom includes many `PathParamSerializer`'s out of the box, such as for `String`, `Long`, `Int`, `Boolean` and `UUID`.  Here's an example of extracting a `long` parameter from the path and passing it to a service call:

@[call-long-id](code/ServiceDescriptors.scala)

Note that this time we're using an [eta-expanded](https://www.scala-lang.org/files/archive/spec/2.12/06-expressions.html#method-values) reference to the method. This is because the method takes a parameter.

Multiple parameters can of course be extracted out, these will be passed to your service call method in the order they are extracted from the URL:

@[call-complex-id](code/ServiceDescriptors.scala)

Query string parameters can also be extracted from the path, using a `&` separated list after a `?` at the end of the path.  For example, the following service call uses query string parameters to implement paging:

@[call-query-string-parameters](code/ServiceDescriptors.scala)

When you use `call`, `namedCall` or `pathCall`, if Lagom maps that down to REST, Lagom will make a best effort attempt to map it down to REST in a semantic fashion. So for example, if there is a request message it will use the `POST` method, whereas if there's none it will use `GET`.

### REST identifiers

The final type of identifier is a REST identifier. REST identifiers are designed to be used when creating semantic REST APIs.  They use both a path, as with the path based identifier, and a request method, to identify them.  They can be configured using the [`restCall`](api/com/lightbend/lagom/scaladsl/api/Service$.html#restCall[Request,Response]\(Method,String,ScalaMethodServiceCall[Request,Response]\)\(MessageSerializer[Request,_],MessageSerializer[Response,_]\):Call[Request,Response]) method:

@[call-rest](code/ServiceDescriptors.scala)

## Messages

Every service call in Lagom has a request message type and a response message type.  When the request or response message isn't used, the `akka.NotUsed` can be used in their place.  Request and response message types fall into two categories, strict and streamed.

### Strict messages

A strict message is a single message that can be represented by a simple Scala object, typically a case class.  The message will be buffered into memory, and then parsed, for example, as JSON.  When both message types are strict, the call is said to be a synchronous call, that is, a request is sent and received, then a response is sent and received.  The caller and callee have synchronized in their communication.

So far, all of the service call examples we've seen have used strict messages, for example, the order service descriptors above accept and return items and orders.  The input value is passed directly to the service call, and returned directly from the service call, and these values are serialized to a JSON buffer in memory before being sent, and read entirely into memory before being deserialized back from JSON.

### Streamed messages

A streamed message is a message of type [`Source`](https://doc.akka.io/api/akka/2.6/akka/stream/scaladsl/Source.html). `Source` is an [Akka streams](https://doc.akka.io/docs/akka/2.6/stream/?language=scala) API that allows asynchronous streaming and handling of messages.  Here's an example streamed service call:

@[call-stream](code/ServiceDescriptors.scala)

This service call has a strict request type and a streamed response type.  An implementation of this might return a `Source` that sends the input tick message `String` at the specified interval.

A bidirectional streamed call might look like this:

@[hello-stream](code/ServiceDescriptors.scala)

In this case, the server might return a `Source` that converts every message received in the request stream to messages prefixed with `Hello`.

Lagom will choose an appropriate transport for the stream, typically, this will be WebSockets.  The WebSocket protocol supports bidirectional streaming, so is a good general purpose option for streaming.  When only one of the request or response message is streamed, Lagom will implement the sending and receiving of the strict message by sending or receiving a single message, and then leaving the WebSocket open until the other direction closes.  Otherwise, Lagom will close the WebSocket when either direction closes.

### Message serialization

Message serializers for requests and responses are provided using type classes.  Each of the `call`, `namedCall`, `pathCall` and `restCall` methods take an implicit [`MessageSerializer`](api/com/lightbend/lagom/scaladsl/api/deser/MessageSerializer.html) for each of the request and response messages. Out of the box Lagom provides a serializer for `String` messages, as well as serializers that implicitly convert a Play JSON [`Format`](https://www.playframework.com/documentation/2.6.x/api/scala/play/api/libs/json/Format.html) type class to a message serializer.

#### Using Play JSON

Play JSON provides a functional type class based library for composing JSON formatters. For detailed documentation on how to use this library, see the [Play documentation](https://www.playframework.com/documentation/2.6.x/ScalaJsonCombinators). For now, we will just look at how to define JSON formats for case classes using Play's JSON format macro.

Let's say you have a `User` case class that looks like this:

@[user-class](code/ServiceDescriptors.scala)

A Play JSON format can be defined on the `User` companion object like so:

@[user-format](code/ServiceDescriptors.scala)

This format will generate and parse JSON in the following format:

```json
{
  "id": 12345,
  "name": "John Smith",
  "email": "john.smith@example.org"
}
```

Fields can be made optional by making them of type `Option`, this will mean the format will not fail to parse the JSON if the property is not present, and when it generates JSON, it will simply not generate that property.

By defining the format on the `User` companion object, we can ensure that this format will be automatically used whenever it is required, due to Scala's implicit scoping rules. This means that aside from declaring the format, no further work needs to be done to ensure that this format will be used for the `MessageSerializer`.

Note that if your case class references another, non primitive type, such as another case class, you'll need to also define a format for that case class.

#### Writing custom message serializers

You can also write custom message serializers, for example, to use protocol buffers or other message format types.  For more information, see the [[message serializers documentation|MessageSerializers]].
