# Message Broker Support

When a service needs data owned by another service, there are two main strategies to obtain the sought data:

1) A service can ask for the data from the service that owns it, and wait until the data is sent back to it. This is a synchronous communication pattern.

2) The system can be architected so that data owned by a service, but needed by other services, is published to an infrastructure component that stores the data for some pre-defined amount of time. This additional component allows publishing and consuming to happen at different times, effectively decoupling services, and hence enabling services to communicate asynchronously.

A sole reliance on synchronous communication between microservices is an architectural smell. A microservices architecture has many moving parts, and this means there is more opportunity for failure. The word synchronous literally means "happening at the same time", synchronous communication implies that both the sender and receiver have to be running at the same time. This means in the face of failure, synchronous communication fails too. This can lead to consistency problems if messages get missed, and can result in a system that is brittle, where a failure in one component leads to the whole system failing.

The solution is to avoid synchronous communication and instead architect your system to communicate asynchronously. As hinted before, one can use an infrastructure component to enable services to communicate asynchronously. This component is commonly referred to as a [message broker](https://en.wikipedia.org/wiki/Message_broker). Various technologies that can be used as a message broker exist, such as [Google Cloud Pub/Sub](https://cloud.google.com/pubsub/docs/overview), [Kafka](https://kafka.apache.org/), and [RabbitMQ](https://www.rabbitmq.com/).

Lagom allows services to easily communicate both synchronously and asynchronously. Both communication strategies have their use, but you should make an effort to architect your system of microservices using asynchronous communication whenever possible. To help you with this, Lagom provides a [[Message Broker API|MessageBrokerApi]] that abstracts over specific message broker technologies, and makes it dead simple for services to share data asynchronously. Currently, Lagom only supports an implementation of the Message Broker API that uses Kafka, but other implementations may become available in the future.
