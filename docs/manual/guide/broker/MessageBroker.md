# Message Broker API

The Lagom Message Broker API provides a distributed publisher-subscriber model that services can use to share data via topics. A topic is simply a channel that allows services to push and pull data. In Lagom, topics are strongly typed, hence both the subscriber and producer can know in advance what is the expected data flowing through.

## Declaring a topic

To publish data to a topic a service needs to declare the topic in its [[service descriptor|ServiceDescriptors#service-descriptors]].

@[hello-service](code/docs/mb/HelloService.java)

The syntax for declaring a topic is similar to the one used already to define services' endpoints. The [`Descriptor#publishing`](api/index.html?com/lightbend/lagom/javadsl/api/Descriptor.html#publishing) method accepts a sequence of topic calls, each topic call can be defined via the [`Service#topic`](api/index.html?com/lightbend/lagom/javadsl/api/Service.html#topic) static method. The latter takes a topic name (i.e., the topic identifier), and a reference to a method that returns a [`Topic`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Topic.html) instance.

Data flowing through a topic is serialized to JSON by default. Of course, it is possible to use a different serialization format, and you can do so by passing a different message serializer for each topic defined in a service descriptor. For instance, using the above service definition, here is how you could have passed a custom serializer: `topic("greetings", this::greetingsTopic).withMessageSerializer(<your-custom-serializer>)`.

## Implementing a topic

To implement a topic you need to inject a [`TopicProducer`](api/index.html?com/lightbend/lagom/javadsl/broker/kafka/TopicProducer.html) in your service implementation class. A topic producer allows to create a topic instance by passing a read-side event stream to it, and a read-side event stream can be easily obtained by using the appropriate api of [`PersistentEntityRegistry`](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntityRegistry.html) ([read the related|ReadSide#raw-stream-of-events] documentation if you don't know what a persisted entity is).

@[inject-topic-producer](code/docs/mb/HelloServiceImpl.java)

And finally we can use both the topic producer and the persistent entity registry to provide an implementation for the topic. 

@[implement-topic](code/docs/mb/HelloServiceImpl.java)

Note that the read-side event stream you passed to the topic producer is "activated" as soon as the service is started. That means all events persisted by your services will eventually be published to the connected topic. Also, you will generally want to map your domain events into some other type, so that other service won't be tightly coupled to another service's domain model events. In other words, domain model events are an implementation detail of the service, and should not be leaked.

### Event Sourcing

The Message Broker API was designed to make it dead simple to publish a read-side event stream to Kafka. Hence, if you have designed your services using [[Event Sourcing (ES)|ES_CQRS]], it will all feel very natural. If you are not using ES, but you'd still like to use Kafka as a message broker, you may use one of the existing Kafka client libraries, in place of the one we provide in Lagom. Our recommendation is to use [akka-kafka-stream](https://github.com/akka/reactive-kafka), because it will be easier to integrate in Lagom, as it exposes a (Akka) streamed based API. In fact, akka-kafka-stream is the library we are using to implement the [[Lagom Kafka Client|KafkaClient]].

## Subscribe to a topic

To subscribe to a topic, a service just need to call [`Topic#subscribe()`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Topic.html#subscribe) on the topic of interest. For instance, imagine that a service wants to collect all greetings messages published by the `HelloService` (refer to the code snippet above). The first thing you should do is injecting `HelloService`.

@[inject-service](code/docs/mb/AnotherServiceImpl.java)

Then, subscribe to the greetings topic, and hook your logic to apply to each messages that published to the topic.

@[subscribe-to-topic](code/docs/mb/AnotherServiceImpl.java)

When calling [`Topic#subscribe()`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Topic.html#subscribe) you get back a [`Subscriber`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Subscriber.html) instance. In the above code snippet we have subscribed to the `greetings` topic using at-least-once delivery semantic. That means each message published to the `greetings` topic is received at least once, but possibly more. The subscriber also offers a [`atMostOnceSource`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Subscriber.html#atMostOnceSource) that gives you at-most-once delivery semantic. If in doubt, prefer using at-least-once delivery semantic.

Finally, subscribers can be grouped together via [`Subscriber#withGroupId`](api/index.html?com/lightbend/lagom/javadsl/api/broker/Subscriber.html#withGroupId). Conceptually you can think of a subscriber group as being a single logical subscriber that happens to be made up of multiple processes. In other words, if multiple subscribers with the same group subscribe to the same topics, only one subscriber in the group will actively consume messages from a topic. Subscriber groups helps you improve resiliency, and allow a pool of processes to divide the work of consuming and processing data.
