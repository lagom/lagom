# Message Serializers

Out of the box, Lagom uses Jackson to serialize request and response messages.  However, you can define custom serializers on a per service call basis, as well register a serializer for a given type for the whole service, and finally you can also customize the serialization factory used by Lagom to completely change the serializers Lagom uses when no serializer is selected.

## How Lagom selects a message serializer

Lagom uses a three step process to select a message serializer for a service call.

### Per service call message serializers

Lagom first checks whether a specific message serializer has been defined on the service call.  By default, each service call defers the decision for request and response message serializers to the next level up, but a custom serializer can be specified for a specific service call request or response, using the [`withRequestSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.Call.html#withRequestSerializer-com.lightbend.lagom.javadsl.api.deser.MessageSerializer-) or [`withResponseSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.Call.html#withResponseSerializer-com.lightbend.lagom.javadsl.api.deser.MessageSerializer-) calls.

This can be overridden when defining the service call in the descriptor:

@[call-serializer](code/docs/services/MessageSerializers.java)

### Per type message serializers

If no message serializer has been provided at the service call level, Lagom then checks whether a serializer has been registered at the service level for that type. Each service maintains a map of types to serializers for that type, and these are used as appropriate for service calls that match the types in the map.

Lagom provides a number of serializers out of the box at this level, including serializers for `String`, `Done`, `NotUsed` and `ByteString`. Note that serializer for `ByteString` doesn't modify data and sends `ByteString` as is. Custom type level serializers can also be supplied in the descriptor using the [`Descriptor.with`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.html#with-java.lang.reflect.Type-com.lightbend.lagom.javadsl.api.deser.MessageSerializer-) method:

@[type-serializer](code/docs/services/MessageSerializers.java)

### Serializer factory

If neither a per service call nor per type message serializer has been found, Lagom will finally request its serializer factory for a serializer for a type.  When using the defaults, this is the way Lagom will usually locate serializers for your types.

Lagom provides a [`SerializerFactory`](api/index.html?com/lightbend/lagom/javadsl/api/deser/SerializerFactory.html) interface for dynamically looking up and creating serializers for types.  The default implementation provided by Lagom is a Jackson serializer factory, which serializes to/from JSON.  You can customize which `SerializerFactory` is used by supplying it to the [`Descriptor.withMessageSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.html#withMessageSerializer-java.lang.Class-com.lightbend.lagom.javadsl.api.deser.MessageSerializer-) method when declaring the signature:

@[with-serializer-factory](code/docs/services/MessageSerializers.java)

## Custom serializers

Of course, being able to configure custom serializers is meaningless if you can't implement custom serializers.  Lagom provides a [`MessageSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/deser/MessageSerializer.html) interface that can be used to implement custom serializers.

As we've [[already seen|ServiceDescriptors#Messages]], there are two types of messages in Lagom, strict messages and streamed messages.  For these two types of messages, Lagom provides two sub interfaces of `MessageSerializer`, [`StrictMessageSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/deser/StrictMessageSerializer.html) and [`StreamedMessageSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/deser/StreamedMessageSerializer.html), which differ primarily in the wire format that they serialize and deserialize to and from.  Strict message serializers serialize and deserialize to and from `ByteString`, that is, they work strictly in memory, while streamed message serializers work with streams, that is, `Source<ByteString, ?>`.

Before we look into how to implement a serializer, there are a few basic concepts that need to be covered.

### Message protocols

Lagom has a concept of message protocols.  Message protocols are expressed using the [`MessageProtocol`](api/index.html?com/lightbend/lagom/javadsl/api/transport/MessageProtocol.html) type, and they have three properties, a content type, a character set, and a version.  All of these properties are optional, and may or may not be used by a message serializer.

Message protocols translate roughly to HTTP `Content-Type` and `Accept` headers, with the version possibly being extracted from these if a mime type scheme that encodes the version is used, or possibly also been extracted from the URL, depending on how the service is configured.

### Content negotiation

Lagom message serializers are able to use content negotiation to decide on the right protocol to use to talk to each other.  This could be used to specify different wire formats, such as JSON and XML, as well as different versions.

Lagom's content negotiation mirrors the same capabilities as HTTP.  For request messages, a client will select whatever protocol it wants to use, and so no negotiation is necessary there.  The server then uses the message protocol sent by the client to decide how to deserialize the request.

For the response, the client sends a list of message protocols that it will accept, and the server should choose a protocol from that list to respond with.  The client will then read the servers chosen protocol, and deserialize the response using that.

### Negotiated serializers

As a consequence of content negotiation, Lagom's `MessageSerializer` doesn't directly serialize and deserialize messages, rather it provides methods for negotiating message protocols, which return a [`NegotiatedSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/deser/MessageSerializer.NegotiatedSerializer.html) or [`NegotiatedDeserializer`](api/index.html?com/lightbend/lagom/javadsl/api/deser/MessageSerializer.NegotiatedDeserializer.html).  It is these negotiated classes that are actually responsible for doing the serializing and deserializing.

Let's take a look at an example of content negotiation.  Let's say we wanted to implement a custom String `MessageSerializer`, that can serialize either to plain text, or to JSON, depending on what the client requests.  This might be useful if you have some clients that send the text body as JSON, while others send it as plain text, perhaps one of the clients was a legacy client that did things one way, but now you want to do it the other with new clients.

Firstly, we'll implement the `NegotiatedSerializer` for plain text Strings:

@[plain-text-serializer](code/docs/services/MessageSerializers.java)

The `protocol` method returns the protocol that this serializer serializes to, and you can see that we are passing the `charset` that this serializer will use in the constructor.  The `serialize` method is a straight forward conversion from `String` to `ByteString`.

Next we'll implement the same thing but to serialize to JSON:

@[json-text-serializer](code/docs/services/MessageSerializers.java)

Here we're using Jackson to convert the `String` to a JSON string.

Now let's implement the plain text deserializer:

@[plain-text-deserializer](code/docs/services/MessageSerializers.java)

Again, we're taking the `charset` as a constructor parameter and we have a straight forward conversion from `ByteString` to `String`.

Likewise, we have a JSON text deserializer:

@[json-text-deserializer](code/docs/services/MessageSerializers.java)

Now that we've implemented our negotiated serializers and deserializers, it's time to implement the `MessageSerializer` to do the actual protocol negotiation.  Our class will extend `StrictMessageSerializer`:

@[text-serializer](code/docs/services/MessageSerializers.java)

The next thing we need to do is define the protocols that we accept.  This will be used by the client to set the `Accept` header:

@[text-serializer-protocols](code/docs/services/MessageSerializers.java)

You can see that this serializer supports both text and json protocols.  One thing to note, we're not setting the charset in the text protocol, this is because we don't need to be specific about it, we can accept any charset that the server chooses.

Now let's implement the `serializerForRequest` method.  This is used by the client to determine which serializer to use for the request.  Because at this stage, no communication has happened between the server and the client, no negotiation can be done, so the client just chooses a default serializer, in this case, a `utf-8` plain text serializer:

@[text-serializer-request](code/docs/services/MessageSerializers.java)

Next we'll implement the `deserializer` method.  This is used both by the server to select the deserializer for the request, and the client to select deserializer for the response.  The passed in `MessageProtocol` is the content type that was sent with the request or response, and we need to inspect it to see if it's a content type that we can deserialize, and return the appropriate content type:

@[text-deserializer](code/docs/services/MessageSerializers.java)

Note that if no content type was specified, we're returning a default deserializer.  We could also fail here by throwing an exception, but it's a good idea not to do that, because some underlying transports don't allow passing a content type with the message.  For example, if this was used for a WebSocket request, web browsers don't allow you to set the content type for a WebSocket request.  By returning a default if no content type is set, we ensure maximum portability.

Next we'll implement the `serializerForResponse` method.  This takes the list of accepted protocols, as sent by the client, and selects one to use to serialize the response.  If it can't find one that it supports, it throws an exception.  Note here that an empty value for any property means that the client is willing to accept anything, likewise if the client didn't specify any accept protocols.

@[text-serializer-response](code/docs/services/MessageSerializers.java)

## Custom serializer factories

As explained before, by default Lagom provides a Jackson serializer factory, but allows you to override it.  A serializer factory is responsible for, given a type, returning a `MessageSerializer` for that type if it can find one.

The [XML serializers](#XML-serializers) example below shows an example of creating a custom serialization factory.

## Examples

### Protocol buffer serializers

[Protocol buffers](https://developers.google.com/protocol-buffers/) are a high performance language neutral alternative to JSON that are particularly a good choice for internal communication between services.  Here's an example of how you might write a `MessageSerializer` for an `Order` class generated by `protoc`:

@[protobuf](code/docs/services/MessageSerializers.java)

Note that this `MessageSerializer` doesn't attempt to do any content negotiation.  In many cases, content negotiation is overkill, if you don't need it, you don't have to implement it.

### XML serializers

Although XML is not recommended due to its size and slow performance, there may be situations where you may need to use it, for example when interfacing with legacy systems.  Here's an example of a JAXB serializer factory:

@[jaxb](code/docs/services/MessageSerializers.java)
