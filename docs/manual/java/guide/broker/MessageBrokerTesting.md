# Message Broker Testing

When decoupling communication via a Broker you can test from both ends of the `Topic`. When your `Service` is publishing events into a `Topic` (as described in [[Declaring a Topic|MessageBrokerApi#Declaring-a-topic]]) your tests should verify the proper data is being pushed into the Topic. At same time, when your service is subscribed to an upstream `Topic` you may want to test how your `Service` behaves when there are incoming events.

A broker will not be started neither when writing publish nor consumption tests. Instead, Lagom provides in-memory implementations of the Broker API in order to make  tests faster. Integration tests with a complete broker should be later implemented but that is out of scope of this documentation. The provided in-memory implementation of the Broker API runs locally and provides exactly-once delivery. If you want to test your code under scenarios where there's message loss (`at-most-once`) or message duplicates (`at-least-once`) you will be responsible for writing such behaviour by injecting duplicates or skipping messages.

The Lagom in-memory broker implementation will also help testing your message serialization and deserialization. That is only available in the tools to [[test publishing|MessageBrokerTesting#Testing-publish]] though since the publishing end is the one responsible to describe the messages being sent over the wire. When you test the consuming end of a topic, no de/serialization will be run under the covers.

The following code samples use the `HelloService` and `AnotherService` already presented in previous sections. `HelloService` publishes `GreetingsMessage`s on the `"greetings"` topic and `AnotherService` subscribed to those messages using `atLeastOnce` semantics.

## Testing publish

When a Service publishes data into a `Topic` the descriptor lists a `TopicCall` on the public API. Testing the event publishing is very similar to testing `ServiceCall`'s in your Service API (see [[Service testing|Test#How-to-test-one-service]]). 

@[topic-test-publishing-into-a-topic](../../../../../testkit/javadsl/src/test/java/com/lightbend/lagom/javadsl/testkit/PublishServiceTest.java)

Using a [`ServiceTest`](api/com/lightbend/lagom/javadsl/testkit/ServiceTest.html) you create a client to your Service. Using that client you can `subscribe` to the published topics. Finally, after interacting with the Service to cause the emission of some events you can assert events were published on the `Topic`.

The producer end is responsible to describe the public API and provide the serializable mappings for all messages exchanged (both in `ServiceCall`s and `TopicCall`s). The tests granting the proper behavior of the publishing operations should also test the serializability and deserializability of the messages.

## Testing subscription

Testing the consumption of messages requires starting the Service under test with a stub of the upstream Service producing data into the topic. The following snippet demonstrates how to achieve it. 

1. A ServiceTest instance is started with a modified `Setup` where the upstream `HelloService` is replaced with a `HelloServiceStub`.
2. An instance of a `ProducerStub` is declared. This instance will be bound when the Server is started and the `HelloServiceStub` created.
3. The Stub for the upstream Service must request a `ProducerStubFactory` from the Injector and use that to obtain a `ProducerStub` for the appropriate `Topic`. See how this snippet uses `GREETINGS_TOPIC` constant declared in the super interface `HelloService`. On the stubbed method that implements the `TopicCall` the stub must return the `Topic` bound to the `ProducerStub` created in the constructor.
4. Use the `ProducerStub` on the tests to send messages into the topic and interact normally with the service under test to verify the Service code. 

@[topic-test-consuming-from-a-topic](code/docs/javadsl/mb/AnotherServiceTest.java)




