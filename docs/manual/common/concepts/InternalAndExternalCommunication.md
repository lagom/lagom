#Internal and external communication

A microservices system usually needs to accomodate the following:

* intra-service communication --- between multiple instances of the same service.

* inter-service communication --- between different services in the same system.

* extra-service communication --- between legacy systems and services outside of your system. 

Within your Lagom system, inter-service and intra-service communication should be asynchronous. Consider a cluster of multiple instances of the same microservice. Each instance needs to keep up to date with work being processed by other instances without blocking. The same principle applies to communication between different services. The Publish-Subscribe model makes it easy to accomplish this. Lagom supports Publish-Subscribe with two levels of guarantees:

* [[Publish-Subscribe|PubSub]] --- for at-most-once semantics. This real-time model supports services that do not care about past events, such as messaging, gaming, auction, sensors, or stock tickers. Limitations include:
<ul><ul>
<li>Network interruptions can cause messages to be lost. </li>
<li>When a new instance starts, joins the cluster, and subscribes, it will not receive messages sent before its subscription.</li>
</ul></ul>  

* Publish with the [[Message Broker API|MessageBrokerAPI]] --- for at-least-once semantics. If a new instance starts publishing information, its messages are added to events previously emitted. If a new instance subscribes to a topic, they will receive all events, past, present, and future (as long as they are subscribed). Lagom implements the API to work with [[Kafka|KafkaServer]], but you could also implement your own. 

> Note: For microservices that use persistent entities, Lagom encourages [[event streaming|ES_CQRS]], which also takes advantage of asynchronous communication and provides guarantees via an event log.

For extra-service communication, third parties can obtain data asychronously from Lagom services that publish to the Broker API and enjoy the same at-least-once guarantee. Lagom services also expose an API for third parties to exchange data synchronously. This is usually mapped to HTTP. Lagom Service APIs also support streaming data to external clients by means of websockets. See [[ServiceDescriptors]] for more information. 


<!---For example, in the following diagram (see slide 5), notice the microservices running in a cluster on separate nodes (JVMs). The microservices in the cluster communicate with each other. Outside the cluster, a Service Gateway, a message broker, and other services also exchange messages. You can choose the type of communication appropriate for each service, whether that is: WebSockets, Akka pub-sub, or the Kafka message broker, and for services that need persistence, event streams. In the example, where all communication is asynchronous, failures or latency will not prevent any individual service from doing its job. -->

 