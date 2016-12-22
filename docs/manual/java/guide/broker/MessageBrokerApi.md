# Message Broker API

The Lagom Message Broker API provides a distributed publish-subscribe model that services can use to share data via topics. A topic is simply a channel that allows services to push and pull data. In Lagom, topics are strongly typed, hence both the subscriber and producer can know in advance what the expected data flowing through will be.

## Declaring a topic

To publish data to a topic a service needs to declare the topic in its [[service descriptor|ServiceDescriptors#service-descriptors]].

@[hello-service](code/docs/mb/HelloService.java)

The syntax for declaring a topic is similar to the one used already to define services' endpoints. The [`Descriptor.publishing`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.html#publishing-com.lightbend.lagom.javadsl.api.Descriptor.TopicCall...-) method accepts a sequence of topic calls, each topic call can be defined via the [`Service.topic`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html#topic-java.lang.String-java.lang.reflect.Method-) static method. The latter takes a topic name (i.e., the topic identifier), and a reference to a method that returns a [`Topic`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Topic.html) instance.

Data flowing through a topic is serialized to JSON by default. Of course, it is possible to use a different serialization format, and you can do so by passing a different message serializer for each topic defined in a service descriptor. For instance, using the above service definition, here is how you could have passed a custom serializer: `topic("greetings", this::greetingsTopic).withMessageSerializer(<your-custom-serializer>)`.

### Partitioning topics

Kafka will distribute messages for a particular topic across many partitions, so that the topic can scale. Messages sent to different partitions may be processed out of order, so if the ordering of the messages you are publishing matters, you need to ensure that the messages are partitioned in such a way that order is preserved.  Typically, this means ensuring each message for a particular entity goes to the same partition.

Lagom allows this by allowing you to configure a partition key strategy, which extracts the partition key out of a message. Kafka will then use this key to help decide what partition to send each message to. The partition can be selected using the [`partitionKeyStrategy`](api/index.html?com/lightbend/lagom/javadsl/api/broker/kafka/KafkaProperties.html#partitionKeyStrategy--) property, by passing a [`PartitionKeyStrategy`](api/index.html?com/lightbend/lagom/javadsl/api/broker/kafka/PartitionKeyStrategy.html) to it: 

@[publishing](code/docs/mb/BlogPostService.java)

## Implementing a topic

The primary source of messages that Lagom is designed to produce is persistent entity events. Rather than publishing events in an ad-hoc fashion in response to particular things happen, it is better to take the stream of events from your persistent entities, and adapt that to a stream of messages sent to the message broker. In this way, you can ensure at least once processing of events by both publishers and consumers, which allows you to guarantee a very strong level of consistency throughout your system.

Lagom's [`TopicProducer`](api/index.html?com/lightbend/lagom/javadsl/broker/TopicProducer.html) helper provides two methods for publishing a persistent entities event stream, [`singleStreamWithOffset`](api/index.html?com/lightbend/lagom/javadsl/broker/TopicProducer.html#singleStreamWithOffset-java.util.function.Function-) for use with non sharded read side event streams, and [`taggedStreamWithOffset`](api/index.html?com/lightbend/lagom/javadsl/broker/TopicProducer.html#taggedStreamWithOffset-org.pcollections.PSequence-java.util.function.BiFunction-) for use with sharded read side event streams.  Both of these methods take a callback which takes the last offset that the topic producer published, and allows resumption of the event stream from that offset via the [`PersistentEntityRegistry.eventStream`](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntityRegistry.html#eventStream-com.lightbend.lagom.javadsl.persistence.AggregateEventTag-com.lightbend.lagom.javadsl.persistence.Offset-) method for obtaining a read-side stream. For more details on read-side streams, see [[Persistent Read-Side's|ReadSide#raw-stream-of-events]].

Lagom will, in the case of the `singleStreamWithOffset` method, ensure that your topic producer only runs on one node of your cluster, or with the `taggedStreamWithOffset` method will distribute the tags evenly across the cluster to distribute the publishing load.

Here's an example of publishing a single, non sharded event stream:

@[implement-topic](code/docs/mb/HelloServiceImpl.java)

Note that the read-side event stream you passed to the topic producer is "activated" as soon as the service is started. That means all events persisted by your services will eventually be published to the connected topic. Also, you will generally want to map your domain events into some other type, so that other service won't be tightly coupled to another service's domain model events. In other words, domain model events are an implementation detail of the service, and should not be leaked.

### Offset storage

Lagom will use your configured persistence API provider to store the offsets for your event streams. To read more about offset storage, see the [[Cassandra offset documentation|ReadSideCassandra#Building-the-read-side-handler]] and [[Relational database offset documentation|ReadSideRDBMS#Building-the-read-side-handler]].

## Subscribe to a topic

To subscribe to a topic, a service just needs to call [`Topic.subscribe()`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Topic.html#subscribe--) on the topic of interest. For instance, imagine that a service wants to collect all greetings messages published by the `HelloService` (refer to the code snippet above). The first thing you should do is inject a `HelloService`.

@[inject-service](code/docs/mb/AnotherServiceImpl.java)

Then, subscribe to the greetings topic, and hook your logic to apply to each messages that published to the topic.

@[subscribe-to-topic](code/docs/mb/AnotherServiceImpl.java)

When calling [`Topic.subscribe()`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Topic.html#subscribe--) you will get back a [`Subscriber`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Subscriber.html) instance. In the above code snippet we have subscribed to the `greetings` topic using at-least-once delivery semantics. That means each message published to the `greetings` topic is received at least once, but possibly more. The subscriber also offers a [`atMostOnceSource`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Subscriber.html#atMostOnceSource--) that gives you at-most-once delivery semantics. If in doubt, prefer using at-least-once delivery semantics.

Finally, subscribers are grouped together via [`Subscriber.withGroupId`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Subscriber.html#withGroupId-java.lang.String-). A subscriber group allows many nodes in your cluster to consume a message stream while ensuring that each message is only handled once by each node in your cluster.  Without subscriber groups, all of your nodes for a particular service would get every message in the stream, leading to their processing being duplicated.  By default, Lagom will use a group id that has the same name as the service consuming the topic.

## Polymorphic event streams

Typically you will want to publish more than one type of event to a particular topic. This can be done by creating an interface that each event implements, and then making the events implement that. In order to successfully serialize these events to and from JSON, a few extra annotations are needed to instruct Jackson to describe and consume the type of the event in the produced JSON.

For example, consider a situation where you have a blog post created event and a blog post published event. Here's what your event structure might look like:

@[content](code/docs/mb/BlogPostEvent.java)

The `@JsonTypeInfo` annotation describes how the type of the event will be serialised. In this case, it's saying each event type will be identified by it's name, and that name will go into a property called `type`. The `@JsonTypeName` on each event subclass says what the name of that event should be. And the `@JsonSubTypes` annotation is used to tell Jackson what the possible sub types of the event are, so that it knows where to look when deserializing.

The resulting JSON for the `BlogPostCreated` event will look like this:

```json
{
  "type": "created",
  "postId": "1234",
  "title": "Some title"
}
```

While the JSON for the `BlogPostPublished` event will look like this:

```json
{
  "type": "published",
  "postId": "1234",
}
```

Finally, note the `defaultImpl = Void.class` in the `@JsonSubTypes` annotation. This tells Jackson that if it comes across an event type that it doesn't recognise the name for, to deserialize it as `null`. This is optional, but can be important for ensuring forwards compatibility in your services, if a service adds a new event subclass that it publishes, often you want your existing services that consume that event stream to just ignore it. Setting this will allow them to do that, otherwise, you'll have to upgrade all the services that consume that event stream to explicitly ignore it before you upgrade the producer that produces the events.
