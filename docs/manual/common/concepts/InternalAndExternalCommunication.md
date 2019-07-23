#Internal and external communication

As discussed in [[Lagom design philosophy|LagomDesignPhilosophy]], services should be isolated and autonomous. Such services communicate with each other (inter-service) by sending messages over a network. To achieve performance and resilience, you will often run multiple instances of the same service, typically on different nodes, and such intra-service communication also goes over the network. In addition, third-party and/or legacy systems might also consume or provide information for your microservice system.

The following topics discuss these communication paths in more detail:

* [Communication within a microservices system](#Communication-within-a-microservices-system)
* [Communication with parties outside of a microservices system](#Communication-with-parties-outside-of-a-microservices-system)

## Communication within a microservices system

While similar in principle, inter- and intra-service communication have very different needs, and Lagom offers multiple implementation options. Inter-service communication must use loosely-coupled protocols and message formats to maintain isolation and autonomy. Coordinating change between different services can be difficult and costly. You can achieve this in your system by taking advantage of the following:

* [[Service calls|ServiceDescriptors]], either synchronous or asynchronous (streaming), allow services to communicate with each other using published APIs and standard protocols (HTTP and WebSockets).

* Publishing messages to a [[broker|MessageBroker]], such as Apache Kafka, decouples communication even further. Lagom's [[Message Broker API|MessageBrokerApi]] provides at-least-once semantics. If a new instance starts publishing information, its messages are added to events previously emitted. If a new instance subscribes to a topic, they will receive all events, past, present, and future (as long as they are subscribed).

Nodes of a single service (collectively called a cluster) require less decoupling. They share the same code and are managed together, as a set, by a single team or individual. For this reason, intra-service communication can take advantage of mechanisms that have less overhead and better performance. For example:

* Many Lagom components use [Akka remoting](https://doc.akka.io/docs/akka/2.6/general/remoting.html) internally, and you can use it directly in your services.

* [[Distributed publish-subscribe|PubSub]] can be used for low-latency, at-most-once messaging between nodes. Limitations include:
<ul><ul>
<li>Network interruptions can cause messages to be lost. </li>
<li>When a new instance starts, joins the cluster, and subscribes, it will not receive messages sent before its subscription.</li>
</ul></ul>

* Databases and other persistent storage can be seen as another way that nodes of a service communicate. For microservices that use persistent entities, Lagom encourages [[event streaming|ES_CQRS]], which also takes advantage of asynchronous communication and provides guarantees via an event log.

This diagram illustrates each of these types of inter- and intra-service communication in a Lagom system distributed across three servers. In the example, the Order service publishes to one or more Kafka topics, while the User service subscribes to consume the information. The User service communicates with other User service instances (cluster members) using Akka remoting. The Shipping service and User service exchange information by streaming it in service calls. [[ServiceCommunication.png]]

## Communication with parties outside of a microservices system

Lagom promotes use of asynchronous communication without preventing use of synchronous communication where necessary. Third parties can obtain data asynchronously from Lagom services that publish to the Broker API and enjoy the at-least-once guarantee. Lagom services also expose an API for third parties to exchange data synchronously. This is usually mapped to HTTP. Lagom Service APIs also support streaming data to external clients by means of websockets. See [[ServiceDescriptors]] for more information.

Interaction with the outside world could mean clients that use the services over the internet, such as browsers, mobile apps or IoT devices. While using standard HTTP or WebSockets, typically such clients do not communicate directly with individual services. Usually, a network boundary acts as a perimeter, and a well-controlled communication point acts as the intermediary between the outside world and the inside world. In Lagom, this communication point is a service gateway. Envision your microservices system as a Medieval town with a wall around it and one gate offers the only way in or out. Communication inside the walls is free and direct, but communication to or from the outside world must come through the service gateway, as illustrated in the following graphic. [[ExtraSystemCommunication.png]]



<!---For example, in the following diagram (see slide 5), notice the microservices running in a cluster on separate nodes (JVMs). The microservices in the cluster communicate with each other. Outside the cluster, a Service Gateway, a message broker, and other services also exchange messages. You can choose the type of communication appropriate for each service, whether that is: WebSockets, Akka pub-sub, or the Kafka message broker, and for services that need persistence, event streams. In the example, where all communication is asynchronous, failures or latency will not prevent any individual service from doing its job. -->


