# Publish-Subscribe

[Publish–subscribe](https://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html) is a well known messaging pattern. Senders of messages, called publishers, do not target the messages directly to specific receivers, but instead publish messages to topics without knowledge of which receivers, called subscribers, if any, there may be. Similarly, a subscriber express interest in a topic and receive messages published to that topic, without knowledge of which publishers, if any, there are.

## Dependency

To use this feature add the following in your project's build:

@[pubsub-dependency](code/pubsub.sbt)

## Usage from Service Implementation

Let's look at an example of a service that publishes temperature measurements of hardware devices. A device can submit its current temperature and interested parties can get a stream of the temperature samples.

The service API is defined as:

@[service-api](code/PubSub.scala)

The implementation of this interface looks like:

@[service-impl](code/PubSub.scala)

When a device submits its current temperature it is published to a topic that is unique for that device. Note that the topic where the message is published to is defined by the message class, here `Temperature`, and an optional classifier, here the device id. The messages of this topic will be instances of the message class or subclasses thereof. The qualifier can be used to distinguish topics that are using the same message class. The empty string can be used as qualifier if the message class is enough to define the topic identity.

Use the method `publish` of the [PubSubRef](api/com/lightbend/lagom/scaladsl/pubsub/PubSubRef.html) representing a given topic to publish a single message, see `registerTemperature` in the above code.

Use the method `subscriber` of the [PubSubRef](api/com/lightbend/lagom/scaladsl/pubsub/PubSubRef.html) to acquire a stream `Source` of messages published to a given topic, see `temperatureStream` in the above code.

It is also possible to publish a stream of messages to a topic as is illustrated by this variant of the `SensorService`:

@[service-impl-stream](code/PubSub.scala)

Note how the incoming `Source` in `registerTemperature` is connected to the `publisher` `Sink` of the topic with the `runWith` method. Also note that we now have an implicit `Materializer` injected into the constructor, this is needed when running a stream. You can of course apply ordinary stream transformations of the incoming stream before connecting it to the `publisher`.

## Usage from Persistent Entity

You can publish messages from a [[Persistent Entity|PersistentEntity]]. First you must inject the [PubSubRegistry](api/com/lightbend/lagom/scaladsl/pubsub/PubSubRegistry.html) to get hold of a `PubSubRef` for a given topic.

@[persistent-entity-inject](code/PubSub.scala)

A command handler that publishes messages, in this case the `PostPublished` event, may look like this:

@[persistent-entity-publish](code/PubSub.scala)

To complete the picture, a service method that delivers these `PostPublished` events as a stream:

@[entity-service-impl](code/PubSub.scala)

## Limitations

This feature is specifically for providing publish and subscribe functionality within a single services cluster. To publish and subscribe between services, you should instead use Lagom's [[message broker support|MessageBrokerApi]].

Published messages may be lost. For example in case of networks problems messages might not be delivered to all subscribers. Future version of Lagom may include intra-service pub-sub with at-least-once delivery, in the meantime you can achieve at-least-once delivery by using Lagom's [[message broker support|MessageBrokerApi]].

Note that anytime you fallback to [[message broker support|MessageBrokerApi]] you will expose your messages via a public topic making them part of your public API.

The registry of subscribers is eventually consistent, i.e. new subscribers are not immediately visible at other nodes, but typically the information will be fully replicated to all other nodes after a few seconds.

## Serialization

The published messages must be serializable since they will be sent across the nodes in the cluster of the service. JSON is the recommended serialization format for these messages. The [[Serialization|Serialization]] section describes how to register serializers for the messages.

## Underlying Implementation

It is implemented with [Akka Distributed Publish Subscribe](https://doc.akka.io/docs/akka/2.6/distributed-pub-sub.html?language=scala).
