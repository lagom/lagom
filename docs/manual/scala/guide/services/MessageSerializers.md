# Message Serializers

Out of the box, Lagom uses Play JSON to serialize request and response messages. You can also define custom serializers to use for your types, using any wire protocol that you please, from JSON to protobufs to XML.

## How Lagom selects a message serializer

When you declare your service descriptor, the `call`, `namedCall`, `pathCall`, `restCall` and `topic` methods all take implicit [`MessageSerializer`](api/com/lightbend/lagom/scaladsl/api/deser/MessageSerializer.html) parameters to handle the messages that your service calls use. As is possible with all implicit parameters in Scala, you can let the Scala compiler resolve these implicitly for you, or you can pass them explicitly.

For example, this shows how to explicitly pass the default Lagom `String` serializer:

@[explicit-serializers](code/MessageSerializers.scala)

We saw in the [[service descriptors|ServiceDescriptors#Using-Play-JSON]] documentation how by declaring an implicit Play JSON `Format` on a case classes companion object, Lagom will use that for messages of that type. The reason this works is that Lagom provides an implicit `MessageSerializer` that wraps a Play JSON `Format`. This is the `jsValueFormatMessageSerializer` method on the `MessageSerializer` companion object.

The `MessageSerializer` companion object also provides implicit for other common, non-JSON payloads you may want to use. For example, anytime your request or response types are `NotUsed`, `Done` or `String` these default serializers will be used. Lagom also ships with support for a `ByteString` serializer (aka `noop`) so the there's direct access to the wire-level payload.

The JSON message serializer format can also be explicitly used. Let's say you had a message with an `id` property, and for one service call you wanted the format used to be the default format that the Play JSON macro gives you, but in another you wanted a different format, one where the `id` field was called `identifier` in the JSON. You might provide two different formats:

@[case-class-two-formats](code/MessageSerializers.scala)

You can see we've made one of these implicit, so it will be picked if we let implicit resolution do its job. Then, the non implicit one can be passed explicitly in the service call descriptor:

@[descriptor-two-formats](code/MessageSerializers.scala)

## Custom serializers

JSON might not be the only type of wire format that you want to use. Lagoms [`MessageSerializer`](api/com/lightbend/lagom/scaladsl/api/deser/MessageSerializer.html) trait can be used to implement custom serializers.

As we've [[already seen|ServiceDescriptors#Messages]], there are two types of messages in Lagom, strict messages and streamed messages.  For these two types of messages, Lagom provides two sub interfaces of `MessageSerializer`, [`StrictMessageSerializer`](api/com/lightbend/lagom/scaladsl/api/deser/StrictMessageSerializer.html) and [`StreamedMessageSerializer`](api/com/lightbend/lagom/scaladsl/api/deser/StreamedMessageSerializer.html), which differ primarily in the wire format that they serialize and deserialize to and from.  Strict message serializers serialize and deserialize to and from `ByteString`, that is, they work strictly in memory, while streamed message serializers work with streams, that is, `Source[ByteString, _]`.

Before we look into how to implement a serializer, there are a few basic concepts that need to be covered.

### Message protocols

Lagom has a concept of message protocols. Message protocols are expressed using the [`MessageProtocol`](api/com/lightbend/lagom/scaladsl/api/transport/MessageProtocol.html) type, and they have three properties, a content type, a character set, and a version.  All of these properties are optional, and may or may not be used by a message serializer.

Message protocols translate roughly to HTTP `Content-Type` and `Accept` headers, with the version possibly being extracted from these if a mime type scheme that encodes the version is used, or possibly also been extracted from the URL, depending on how the service is configured.

### Content negotiation

Lagom message serializers are able to use content negotiation to decide on the right protocol to use to talk to each other.  This could be used to specify different wire formats, such as JSON and XML, as well as different versions.

Lagom's content negotiation mirrors the same capabilities as HTTP.  For request messages, a client will select whatever protocol it wants to use, and so no negotiation is necessary there.  The server then uses the message protocol sent by the client to decide how to deserialize the request.

For the response, the client sends a list of message protocols that it will accept, and the server should choose a protocol from that list to respond with.  The client will then read the servers chosen protocol, and deserialize the response using that.

### Negotiated serializers

As a consequence of content negotiation, Lagom's `MessageSerializer` doesn't directly serialize and deserialize messages, rather it provides methods for negotiating message protocols, which return a [`NegotiatedSerializer`](api/com/lightbend/lagom/scaladsl/api/deser/MessageSerializer$$NegotiatedSerializer.html) or [`NegotiatedDeserializer`](api/com/lightbend/lagom/scaladsl/api/deser/MessageSerializer$$NegotiatedDeserializer.html).  It is these negotiated classes that are actually responsible for doing the serializing and deserializing.

Let's take a look at an example of content negotiation.  Let's say we wanted to implement a custom String `MessageSerializer`, that can serialize either to plain text, or to JSON, depending on what the client requests.  This might be useful if you have some clients that send the text body as JSON, while others send it as plain text, perhaps one of the clients was a legacy client that did things one way, but now you want to do it the other with new clients.

Firstly, we'll implement the `NegotiatedSerializer` for plain text Strings:

@[plain-text-serializer](code/MessageSerializers.scala)

The `protocol` method returns the protocol that this serializer serializes to, and you can see that we are passing the `charset` that this serializer will use in the constructor.  The `serialize` method is a straight forward conversion from `String` to `ByteString`.

Next we'll implement the same thing but to serialize to JSON:

@[json-text-serializer](code/MessageSerializers.scala)

Here we're using Play JSON to convert the `String` to a JSON string.

Now let's implement the plain text deserializer:

@[plain-text-deserializer](code/MessageSerializers.scala)

Again, we're taking the `charset` as a constructor parameter and we have a straight forward conversion from `ByteString` to `String`.

Likewise, we have a JSON text deserializer:

@[json-text-deserializer](code/MessageSerializers.scala)

Now that we've implemented our negotiated serializers and deserializers, it's time to implement the `MessageSerializer` to do the actual protocol negotiation.  Our class will extend `StrictMessageSerializer`:

@[text-serializer](code/MessageSerializers.scala)

The next thing we need to do is define the protocols that we accept.  This will be used by the client to set the `Accept` header:

@[text-serializer-protocols](code/MessageSerializers.scala)

You can see that this serializer supports both text and json protocols.  One thing to note, we're not setting the charset in the text protocol, this is because we don't need to be specific about it, we can accept any charset that the server chooses.

Now let's implement the `serializerForRequest` method.  This is used by the client to determine which serializer to use for the request.  Because at this stage, no communication has happened between the server and the client, no negotiation can be done, so the client just chooses a default serializer, in this case, a `utf-8` plain text serializer:

@[text-serializer-request](code/MessageSerializers.scala)

Next we'll implement the `deserializer` method.  This is used both by the server to select the deserializer for the request, and the client to select deserializer for the response.  The passed in `MessageProtocol` is the content type that was sent with the request or response, and we need to inspect it to see if it's a content type that we can deserialize, and return the appropriate content type:

@[text-deserializer](code/MessageSerializers.scala)

Note that if no content type was specified, we're returning a default deserializer.  We could also fail here by throwing an exception, but it's a good idea not to do that, because some underlying transports don't allow passing a content type with the message.  For example, if this was used for a WebSocket request, web browsers don't allow you to set the content type for a WebSocket request.  By returning a default if no content type is set, we ensure maximum portability.

Next we'll implement the `serializerForResponse` method.  This takes the list of accepted protocols, as sent by the client, and selects one to use to serialize the response.  If it can't find one that it supports, it throws an exception.  Note here that an empty value for any property means that the client is willing to accept anything, likewise if the client didn't specify any accept protocols.

@[text-serializer-response](code/MessageSerializers.scala)

## Examples

### Protocol buffer serializers

[Protocol buffers](https://developers.google.com/protocol-buffers/) are a high performance language neutral alternative to JSON that are particularly a good choice for internal communication between services.  Here's an example of how you might write a `MessageSerializer` for an `Order` class generated by `protoc`:

@[protobuf](code/MessageSerializers.scala)

Note that this `MessageSerializer` doesn't attempt to do any content negotiation.  In many cases, content negotiation is overkill, if you don't need it, you don't have to implement it.
