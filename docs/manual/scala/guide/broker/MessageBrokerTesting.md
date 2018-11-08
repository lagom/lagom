# Message Broker Testing

When decoupling communication via a Broker you can test from both ends of the `Topic`. When your `Service` is publishing events into a `Topic` (as described in [[Declaring a Topic|MessageBrokerApi#Declaring-a-topic]]) your tests should verify the proper data is being pushed into the Topic. At same time, when your service is subscribed to an upstream `Topic` you may want to test how your `Service` behaves when there are incoming events.

A broker will not be started neither when writing publish nor consumption tests. Instead, Lagom provides in-memory implementations of the Broker API in order to make  tests faster. Integration tests with a complete broker should be later implemented but that is out of scope of this documentation. The provided in-memory implementation of the Broker API runs locally and provides exactly-once delivery. If you want to test your code under scenarios where there's message loss (`at-most-once`) or message duplicates (`at-least-once`) you will be responsible for writing such behaviour by injecting duplicates or skipping messages.

The Lagom in-memory broker implementation will also help testing your message serialization and deserialization. That is only available in the tools to [[test publishing|MessageBrokerTesting#Testing-publish]] though since the publishing end is the one responsible to describe the messages being sent over the wire. When you test the consuming end of a topic, no de/serialization will be run under the covers.

The following code samples use the `HelloService` and `AnotherService` already presented in previous sections. `HelloService` publishes `GreetingsMessage`s on the `"greetings"` topic and `AnotherService` subscribed to those messages using `atLeastOnce` semantics.


## Testing publish

When a Service publishes data into a `Topic` the descriptor lists a `TopicCall` on the public API. Testing the event publishing is very similar to testing `ServiceCall`'s in your Service API (see [[Service testing|TestingServices#How-to-test-one-service]]). 

@[topic-test-publishing-into-a-topic](code/docs/scaladsl/mb/PublishServiceSpec.scala)

In order to start the application with a stubbed broker you will have to mixin a `TestTopicComponents` into your test application.

Use a [`ServiceTest`](api/com/lightbend/lagom/scaladsl/testkit/ServiceTest$.html) you to create a client to your Service and using that client you can `subscribe` to the published topics. Finally, after interacting with the Service to cause the emission of some events you can assert events were published on the `Topic`.

The producer end is responsible to describe the public API and provide the serializable mappings for all messages exchanged (both in `ServiceCall`s and `TopicCall`s). The tests granting the proper behavior of the publishing operations should also test the serializability and deserializability of the messages.


## Testing subscription

Testing the consumption of messages requires starting the Service under test with a stub of the upstream Service producing data into the topic. The following snippet demonstrates how to achieve it. 

1. An in-memory `Topic` is required and means to send messages into that in-mem Topic. Using the `ProducerStubFactory` it's possible to obtain a `ProducerStub` given a topic name.
2. With the `producerStub` instance a service stub can be build to replace the production ready upstream service. This will have to use the topic bound to the `ProducerStub` created in the previous step.
3. Use the `ProducerStub` on the tests to send messages into the topic and interact normally with the service under test to verify the Service code. 

@[topic-test-consuming-from-a-topic](code/docs/scaladsl/mb/AnotherServiceSpec.scala)

When testing a subscription it is possible the code under test includes a Service that is a producer itself. In those situations, the `Application` used for the unit tests must differ from the `Application` used for production. The `Application` used in unit tests must not mix-in `LagomKafkaComponents` and just use `TestTopicComponents` instead.

