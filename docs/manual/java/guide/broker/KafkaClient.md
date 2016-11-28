# Lagom Kafka Client

Lagom provides an implementation of the Message Broker API that uses Kafka. In the remainder you will learn how to add the dependency in your build, and how to configure and tune topic's publishers and subscribers.

## Dependency

To use this feature add the following in your project's build. 

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-kafka-broker_2.11</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[kafka-broker-dependency](code/build-kafka.sbt)

When importing the Lagom Kafka Broker module keep in mind that the Lagom Kafka Broker module requires one implementation of a Lagom Persistence so make sure your dependencies include either [[Lagom Persistence Cassandra|PersistentEntityCassandra]] or [[Lagom Persistence JDBC|PersistentEntityRDBMS]]


## Configuration

The Lagom Kafka Client implementation is built using [akka-stream-kafka](https://github.com/akka/reactive-kafka). The akka-stream-kafka library wraps the official [Apache Java Kafka client](http://kafka.apache.org/documentation.html) and exposes a (Akka) stream based API to publish/consume messages to/from Kafka. Therefore, we have effectively three libraries at play, with each of them exposing its own configuration. Let's explore  the configuration keys exposed by each layer, starting with the one sitting at the top, i.e., the Lagom Kafka Client.

### Lagom Kafka Client

@[kafka-broker](../../../../../service/core/kafka/client/src/main/resources/reference.conf)

First, notice you can pass the location of your Kafka brokers via the key `lagom.broker.kafka.brokers`. In production, you will usually want to have at least two brokers for resiliency. Make sure to separate each broker URL with a comma.

Second, we have configuration that is specific to the publisher and the subscriber. The `lagom.broker.kafka.client.default.failure-exponential-backoff` defines configuration for what to do when a publisher or subscriber stream fails. Specifically, it allows you to configure the backoff time that is awaited before restarting a publishing/consuming stream. Failure can happen for different reasons, for instance it may be due to an application error, or because of a network error. Independently of the cause, Lagom will keep retrying to restart the stream (whilst waiting longer and longer between each failed retry). As you can see, both the publisher and subscriber use the same defaults, but different values for either of them can be set.

Third, the consumer has a few more configuration keys allowing you to decide how often the read-side offset is persisted in the datastore. When tuning these values, you are trading performances (storing the offset every time a message is consumed can be costly), with the risk of having to re-process some message if a failure occurs.

### Akka Stream Kafka configuration

See the [akka-stream-kafka reference.conf](https://github.com/akka/reactive-kafka/blob/master/core/src/main/resources/reference.conf) to find out about the available configuration parameters.

### Apache Java Kafka Client

See the [Producer Configs](http://kafka.apache.org/documentation.html#producerconfigs) documentation to learn about the exposed configuration for the publisher. While, for the subscriber, see the [New Consumer Configs](http://kafka.apache.org/documentation.html#newconsumerconfigs). The only caveat is that if you need to change the value of any of the configuration provided by the Java Kafka Client, you must prepend the desired configuration key you want to change with `akka.kafka.consumer.kafka-clients`, for the consumer, or `akka.kafka.producer.kafka-clients`. For instance, let's assume you'd like to change the consumer's `request.timeout.ms`, you should add the following in the service's application.conf:

```conf
akka.kafka.producer.kafka-clients {
  request.timeout.ms = 30000
}
```

## Subscriber only Services

Sometimes you will implement a Lagom Service that will only consume from the Kafka Topic. In that case you can import the Lagom Kafka Client alone (instead of importing the Lagom Kafka Broker and a Lagom Persistence implementation).

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-kafka-client_2.11</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[kafka-client-dependency](code/build-kafka.sbt)

If/when your subscriber-only service evolves to include features that publish data to a topic, you will need to depend on Lagom Kafka Broker and remove the dependency to Lagom Kafka Client. The Lagom Kafka Broker module includes the Lagom Kafka Client module. 

### Consuming Topics from 3rd parties

You may want your Lagom service to consume data produced on services not implemented in Lagom. In that case, as described in the [[Service Clients|ServiceClients]] section, you can create a `third-party-service-api` module in your Lagom project. That module will contain a Service Descriptor [[declaring the topic|MessageBrokerApi#Declaring-a-topic]] you will consume from. Once you have your `ThirdPartyService` interface and related classes implemented, you should add `third-party-service-api` as a dependency on your `fancy-service-impl`. Finally, you can consume from the topic described in `ThirdPartyService` as documented in the [[Subscribe to a topic|MessageBrokerApi#Subscribe-to-a-topic]] section.