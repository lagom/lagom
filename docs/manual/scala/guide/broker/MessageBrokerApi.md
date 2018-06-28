# Message Broker API

The Lagom Message Broker API provides a distributed publish-subscribe model that services can use to share data via topics. A topic is simply a channel that allows services to push and pull data. In Lagom, topics are strongly typed, hence both the subscriber and producer can know in advance what the expected data flowing through will be.

## Declaring a topic

To publish data to a topic a service needs to declare the topic in its [[service descriptor|ServiceDescriptors#Service-Descriptors]].

@[hello-service](code/docs/scaladsl/mb/HelloService.scala)

The syntax for declaring a topic is similar to the one used already to define services' endpoints. The [`Descriptor.withTopics`](api/com/lightbend/lagom/scaladsl/api/Descriptor.html) method accepts a sequence of topic calls, each topic call can be defined via the [`topic`](api/com/lightbend/lagom/scaladsl/api/Service$.html) method on the `Service` object. The latter takes a topic name (i.e., the topic identifier), and a method reference that returns a [`Topic`](api/com/lightbend/lagom/scaladsl/api/broker/Topic.html) instance.

Data flowing through a topic is serialized to JSON by default. Of course, it is possible to use a different serialization format, and you can do so by providing an implicit message serializer for each topic defined in a service descriptor. This is the same approach described on [[message serialization|ServiceDescriptors#Message-serialization]] when presenting [[Service Descriptors|ServiceDescriptors]]. You may want to review the [[message serializers documentation|MessageSerializers]] too.

### Partitioning topics

Kafka will distribute messages for a particular topic across many partitions, so that the topic can scale. Messages sent to different partitions may be processed out of order, so if the ordering of the messages you are publishing matters, you need to ensure that the messages are partitioned in such a way that order is preserved.  Typically, this means ensuring each message for a particular entity goes to the same partition.

Lagom allows this by allowing you to configure a partition key strategy, which extracts the partition key out of a message. Kafka will then use this key to help decide what partition to send each message to. The partition can be selected using the [`partitionKeyStrategy`](api/com/lightbend/lagom/scaladsl/api/broker/kafka/KafkaProperties$.html#partitionKeyStrategy[Message]:com.lightbend.lagom.scaladsl.api.Descriptor.Property[Message,com.lightbend.lagom.scaladsl.api.broker.kafka.PartitionKeyStrategy[Message]]) property, by passing a [`PartitionKeyStrategy`](api/com/lightbend/lagom/scaladsl/api/broker/kafka/PartitionKeyStrategy.html) to it:

@[withTopics](code/docs/scaladsl/mb/BlogPostService.scala)

## Implementing a topic

The primary source of messages that Lagom is designed to produce is persistent entity events. Rather than publishing events in an ad-hoc fashion in response to particular things happening, it is better to take the stream of events from your persistent entities, and adapt that to a stream of messages sent to the message broker. In this way, you can ensure at least once processing of events by both publishers and consumers, which allows you to guarantee a very strong level of consistency throughout your system.

Lagom's [`TopicProducer`](api/com/lightbend/lagom/scaladsl/broker/TopicProducer$.html) helper provides two methods for publishing a persistent entities event stream, [`singleStreamWithOffset`](api/com/lightbend/lagom/scaladsl/broker/TopicProducer$.html) for use with non sharded read side event streams, and [`taggedStreamWithOffset`](api/com/lightbend/lagom/scaladsl/broker/TopicProducer$.html) for use with sharded read side event streams.  Both of these methods take a callback which takes the last offset that the topic producer published, and allows resumption of the event stream from that offset via the [`PersistentEntityRegistry.eventStream`](api/com/lightbend/lagom/scaladsl/persistence/PersistentEntityRegistry.html) method for obtaining a read-side stream. For more details on read-side streams, see [[Persistent Read-Side's|ReadSide#Raw-Stream-of-Events]].

Lagom will, in the case of the `singleStreamWithOffset` method, ensure that your topic producer only runs on one node of your cluster, or with the `taggedStreamWithOffset` method will distribute the tags evenly across the cluster to distribute the publishing load.

Here's an example of publishing a single, non sharded event stream:

@[implement-topic](code/docs/scaladsl/mb/HelloServiceImpl.scala)

Note that the read-side event stream you passed to the topic producer is "activated" as soon as the service is started. That means that in the previous example, all events persisted by your services will eventually be published to the connected topic. Also, you will generally want to map your domain events into some other type, so that other service won't be tightly coupled to another service's domain model events. In other words, domain model events are an implementation detail of the service, and should not be leaked.

### Filtering events

You may not want all events persisted by your services to be published. If that is the case then you can filter the event stream:

@[filter-events](code/docs/scaladsl/mb/FilteredServiceImpl.scala)

When an event is filtered, the `TopicProducer` does not publish the event. It also does not advance the offset. If the `TopicProducer` restarts then it will resume from the last offset. If a large number of events are filtered then the last offset could be quite far behind, and so all those events will be reprocessed and filtered out again. You need to be aware that this may occur and keep the number of consecutively filtered elements relatively low and also minimize the time and resources required to perform the filtering.

### Offset storage

Lagom will use your configured persistence API provider to store the offsets for your event streams. To read more about offset storage, see the [[Cassandra offset documentation|ReadSideCassandra#Building-the-read-side-handler]], [[JDBC database offset documentation|ReadSideJDBC#Building-the-read-side-handler]] and [[Slick database offset documentation|ReadSideSlick#Building-the-read-side-handler]].

## Subscribe to a topic

To subscribe to a topic, a service just needs to call [`Topic.subscribe`](api/com/lightbend/lagom/scaladsl/api/broker/Topic.html) on the topic of interest. For instance, imagine that a service wants to collect all greetings messages published by the `HelloService`. The first thing you should do is inject a `HelloService` (See the section on [[using service clients|ServiceClients#Using-a-service-client]] for a complete explanation on using a client to another service). Then, subscribe to the greetings topic, and hook your logic to apply to each messages that published to the topic.

@[subscribe-to-topic](code/docs/scaladsl/mb/AnotherServiceImpl.scala)

When calling [`Topic.subscribe`](api/com/lightbend/lagom/scaladsl/api/broker/Topic.html#subscribe:com.lightbend.lagom.scaladsl.api.broker.Subscriber[Message]) you will get back a [`Subscriber`](api/com/lightbend/lagom/scaladsl/api/broker/Subscriber.html) instance. In the above code snippet we have subscribed to the `greetings` topic using at-least-once delivery semantics. That means each message published to the `greetings` topic is received at least once, but possibly more. The subscriber also offers a [`atMostOnceSource`](api/com/lightbend/lagom/scaladsl/api/broker/Subscriber.html#atMostOnceSource:akka.stream.scaladsl.Source[Message,_]) that gives you at-most-once delivery semantics. If in doubt, prefer using at-least-once delivery semantics.

Finally, subscribers are grouped together via [`Subscriber.withGroupId`](api/com/lightbend/lagom/scaladsl/api/broker/Subscriber.html#withGroupId\(groupId:String\):com.lightbend.lagom.scaladsl.api.broker.Subscriber[Message]). A subscriber group allows many nodes in your cluster to consume a message stream while ensuring that each message is only handled once by each node in your cluster.  Without subscriber groups, all of your nodes for a particular service would get every message in the stream, leading to their processing being duplicated.  By default, Lagom will use a group id that has the same name as the service consuming the topic.

### Consuming message metadata

Your broker implementation may provide additional metadata with messages which you can consume. This can be accessed by invoking the [`Subscriber.withMetadata`](api/com/lightbend/lagom/scaladsl/api/broker/Subscriber.html#withMetadata:com.lightbend.lagom.scaladsl.api.broker.Subscriber[com.lightbend.lagom.scaladsl.api.broker.Message[Payload]]) method, which returns a subscriber that wraps the messages in a [`Message`](api/com/lightbend/lagom/scaladsl/api/broker/Message.html).

@[subscribe-to-topic-with-metadata](code/docs/scaladsl/mb/AnotherServiceImpl.scala)

The [`messageKeyAsString`](api/com/lightbend/lagom/scaladsl/api/broker/Message.html#messageKeyAsString:String) method is provided as a convenience for accessing the message key. Other properties can be accessed using the [`get`](api/com/lightbend/lagom/scaladsl/api/broker/Message.html#get\(com.lightbend.lagom.scaladsl.api.broker.MetadataKey[Metadata]\):Metadata) method. A full list of the metadata keys available for Kafka can be found [here](api/com/lightbend/lagom/scaladsl/broker/kafka/KafkaMetadataKeys$.html).

### Skipping messages

You may only want to apply your logic to a subset of the messages that the topic publishes and skip the others. The `Flow` that is passed to [`Subscriber.atLeastOnce`](api/com/lightbend/lagom/scaladsl/api/broker/Subscriber.html#atLeastOnce\(flow:akka.stream.scaladsl.Flow[Payload,akka.Done,_]\):scala.concurrent.Future[akka.Done]) must emit exactly one `Done` element for each element that it receives. It must also emit them in the same order that the elements were received. This means that you must not use methods such as `filter` or `collect` on the `Flow` which would drop elements.

The easiest way to achieve this is to use a total function which returns `Done` for the elements that should be skipped. For example:

@[subscribe-to-topic-skip-messages](code/docs/scaladsl/mb/AnotherServiceImpl.scala)

## Polymorphic event streams

Typically you will want to publish more than one type of event to a particular topic. This can be done by creating an interface that each event implements. In order to successfully serialize these events to and from JSON, you will have to include some extra information on your JSON representation of the data.

For example, consider a situation where you have a blog post created event and a blog post published event. Here's what your event structure might look like:

@[content](code/docs/scaladsl/mb/BlogPostService.scala)

And that's how your Play JSON formatters could look like:

@[content-formatters](code/docs/scaladsl/mb/BlogPostService.scala)

You will have to implement a custom Message Serializer that adds extra information on each JSON message so that you know what deserializer to use on the other end. The resulting JSON for the `BlogPostCreated` event will look like this:

```json
{
 "postId": "23",
 "title": "new post",
 "event_type": "postCreated"
}
```

While the JSON for the `BlogPostPublished` event will look like this:

```json
{
 "postId": "23",
 "event_type": "postPublished"
}
```

You can do that using [Play JSON transformers](https://www.playframework.com/documentation/2.6.x/ScalaJsonTransformers#Case-5:-Put-a-given-value-in-a-new-branch):

@[polymorphic-play-json](code/docs/scaladsl/mb/BlogPostService.scala)


This approach has an added maintenance cost. Fortunately there are libraries that expand Play JSON features and provide support for algebraic data type serialization. For example: [Play JSON Derived Codecs](https://github.com/julienrf/play-json-derived-codecs).
