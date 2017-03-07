# Scala Components

The Lagom scaladsl requires building an Application that mixes in all the Components that will enable the required features for your code. This means each Application will need a specific set of cake slices. Not only that but `Dev` mode and `Prod` mode need adjusting the cake since the environments where the Service is run will vary.

### A brief intro to Cake Pattern

The cake pattern is a design pattern for Dependency Injection in scala where an application (the cake) is built as by mixing in several traits (the cake slices). Each trait may declare unimplemented `def` methods stating what it requires to run. Each trait may provide implementations the final application will need. To present an example, many Lagom Components (or cake layers) declare a need for a `ServiceLocator` and as a developer you are required to provide one when using one of those Components. If you don't fulfill all the declared requirements for the cake slices you mix into your cake, it will be incomplete and compilation will fail.

Let's see an example with actual Lagom Scala API code. Imagine you have a `HelloService` (and a `HelloServiceImpl`) and you are building your Application cake. You could prepare:

@[lagom-application](code/ServiceImplementation.scala)

That is a simple enough Application but if you pay close attention you'll notice it's is an `abstract class`. The `HelloApplication` builds all the basics for the application to be run but it's not complete. It extends [`LagomApplication`](api/com/lightbend/lagom/scaladsl/server/LagomApplication.html) so it needs a `serviceLocator: ServiceLocator`, a `lagomService: LagomServer` and a `wcClient: WSClient`. We provide the `wcClient: WSClient` mixing in `AhcWSComponents` and we provide `lagomService: LagomServer` programmatically. We still need a `serviceLocator: ServiceLocator` but we need to specify a different [[Service Locator|ServiceLocator]] for each environment we want our app to run in (development environment or production environment):

@[lagom-loader](code/ServiceImplementation.scala)

In the `ApplicationLoader` depending on the runtime environment we will complete the cake in a slightly different way. See how the `ApplicationLoader` defines two methods: `loadDevMode()` and `load()` so you can mix-in different ServiceLocators. In `loadDevMode()` we mix in `LagomDevModeComponents` which provide a Service Locator for Lagom's development mode environment and will also add a feature into our service to self register when the service boots up.

This is a brief introduction to Lagom Application cake, there's more information in [[Wiring together a Lagom application|ServiceImplementation#Wiring-together-a-Lagom-application]].

## Components

This is a list of available `Components` you may use to build your application cake. This list contains Components provided by Lagom, Play and other projects or libraries. You may develop your own Components and use those instead of the provided on this list as long as the contracts are fulfilled.

|  Service Components  ||
| -------------------- | ----------- |
| [LagomServerComponents](api/com/lightbend/lagom/scaladsl/server/LagomServerComponents.html) | a main Component for any Lagom Service. See [[Building a cake step by step|ScalaComponents#Building-a-cake-step-by-step]].|
| [LagomServiceClientComponents](api/com/lightbend/lagom/scaladsl/client/LagomServiceClientComponents.html) | a main Component for any Lagom Service or application consuming Lagom Services. See [[Building a cake step by step|ScalaComponents#Building-a-cake-step-by-step]] and  [[Binding a service client|ServiceClients#Binding-a-service-client]].|
| [MetricsServiceComponents](api/com/lightbend/lagom/scaladsl/server/status/MetricsServiceComponents.html) | adds a `MetricsService` to your service so you can remotely track the status of the CircuitBreakers on your service. Using this only makes sense when you Application is consuming other services (hence using remote calls protected with Circuit Breakers). See [[Circuit Breaker Metrics|ServiceClients#Circuit-breaker-metrics]] |

#### Building a cake step by step

`LagomServerComponents` and `LagomServiceClientComponents` are a bit special because they are already mixed into `LagomApplication` and `LagomClientApplication` respectively so you will rarely need to explicitly mix either of them into your Application. The reason why `LagomServerComponents` and `LagomServiceClientComponents` exist is so that you can build your Application cake as the sum of middle-sized portions. Instead of creating a single `abstract class` where you mix in all the Componenets, you may create one trait `MyPartialFooTrait` where you mix in `LagomServerComponents` and parts of your logic and then you could create `MyPartialBarTrait` with some other parts. Then you would build a `MyApplication` mixing in `MyPartialFooTrait` and `MyPartialBarTrait`. so far it's not very different from having a single Application where all Components are mixed. The key advantage of partially creating your cake is that you can use `MyPartialTrait` in isolation in your tests (instead of using a complete `MyApplication`) which will be lighter and provide decoupling.

| Persistence and Cluster Components  ||
| -------------------- | ----------- |
| [ClusterComponents](api/com/lightbend/lagom/scaladsl/cluster/ClusterComponents.html) |  registers the node to the Akka Cluster. Tha Akka Cluster is required by Pub-Sub and Persistent Entity support and you will rarely need to use it explicitly. See [[Cluster]]|
| [PubSubComponents](api/com/lightbend/lagom/scaladsl/pubsub/PubSubComponents.html) | provides [[Publish-Subscribe|PubSub#Publish-Subscribe]]. This requires `ClusterComponents`. See [[Publish-Subscribe|PubSub#Publish-Subscribe]]|
| [CassandraPersistenceComponents](api/com/lightbend/lagom/scaladsl/persistence/cassandra/CassandraPersistenceComponents.html) |  provides both Read-Side and Write-Side components for Cassandra-backed CQRS. It provides `ReadSideCassandraPersistenceComponents` and `WriteSideCassandraPersistenceComponents` which you might want to use in isolation. See [[PersistentEntityCassandra]] for more info. |
| [JdbcPersistenceComponents](api/com/lightbend/lagom/scaladsl/persistence/jdbc/JdbcPersistenceComponents.html) | provides both Read-Side and Write-Side components for Cassandra-backed CQRS. It provides `ReadSideJdbcPersistenceComponents`and `WriteSideJdbcPersistenceComponents` which you might want to use in isolation. See [[PersistentEntityRDBMS]]. |


| Broker API Components ||
| -------------------- | ----------- |
| [LagomKafkaClientComponents](api/com/lightbend/lagom/scaladsl/broker/kafka/LagomKafkaClientComponents.html) | provide a Kafka implementation for the Broker API so that you Service can subscribe to a Kafka topic. See[[KafkaClient|KafkaClient#Subscriber-only-Services]]. |
| [LagomKafkaComponents](api/com/lightbend/lagom/scaladsl/broker/kafka/LagomKafkaComponents.html) | provides a Kafka implementation for the Broker API so that you Service can `publish` to a Kafka topic. This component includes `LagomKafkaClientComponents` so if you mix this one in, you will be able to `publish` and `subscribe`. See [[Kafka Client|KafkaClient#Dependency]]. |
| [TestTopicComponents](api/com/lightbend/lagom/scaladsl/testkit/TestTopicComponents.html) | provides stubbing tools to test services that use the Broker API. This is meant to be used only on Applications you build on your tests. See [[Testing publish|MessageBrokerTesting#Testing-publish]] |


| Service Locator Components ||
| -------------------- | ----------- |
| [LagomDevModeServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/devmode/LagomDevModeServiceLocatorComponents.html) | provides the dev mode service locator. This is meant to be used by Lagom Services and other applications such as Play Apps that want to interact with Lagom Services in Dev Mode. See the [scaladocs](api/com/lightbend/lagom/scaladsl/devmode/LagomDevModeServiceLocatorComponents.html) for more details. |
| [LagomDevModeComponents](api/com/lightbend/lagom/scaladsl/devmode/LagomDevModeComponents.html) | provides the dev mode service locator and registers the services with it in dev mode. See [[Wiring together a Lagom application|ServiceImplementation#Wiring-together-a-Lagom-application]].|
| [StaticServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/client/StaticServiceLocatorComponents.html)| provides a Service Locator that always resolves the same URI for a given Service name. |
| [ConfigurationServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocatorComponents.html)| provides a Service Locator based on `application.conf` files. See [[deploying using static service locations|ProductionOverview#Deploying-using-static-service-locations]].|
| [RoundRobinServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/client/RoundRobinServiceLocatorComponents.html)| provides a Service Locator that applies a Round Robin over the passed in sequence of URI. |
| [CircuitBreakerComponents](api/com/lightbend/lagom/scaladsl/client/CircuitBreakerComponents.html)| implementors of a Service Locator will need to extend this to reuse the Circuit Breaker config provided by Lagom.|



You can mix in `Components` from other frameworks or libraries, for example:

 * [ConductRApplicationComponents](https://github.com/typesafehub/conductr-lib/blob/master/lagom1-scala-conductr-bundle-lib/src/main/scala/com/typesafe/conductr/bundlelib/lagom/scaladsl/ConductRApplicationComponents.scala): provides a Service Locator provided by ConductR, reads any ConductR provided configuration and makes the service register into ConductR's Service Registry. See [[ConductR]]
 * [AhcWSComponents](https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.libs.ws.ahc.AhcWSComponents): provides a `WSClient` based on an Async HTTP Client.
 * [DBComponents](https://www.playframework.com/documentation/2.5.x/api/scala/play/api/db/DBComponents.html)
 * [HikariCPComponents](https://www.playframework.com/documentation/2.5.x/api/scala/play/api/db/HikariCPComponents.html)

You can find a complete list of inherited `Components` in [Play docs](https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.db.HikariCPComponents).
