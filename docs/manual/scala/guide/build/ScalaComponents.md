# Scala Components

The Lagom scaladsl requires building an Application that mixes in all the `Components` that will enable the required features for your code. This means each `Application` will need a specific set of cake slices. Not only that but `Dev` mode and `Prod` mode need adjusting since the environments where the Service is run vary.

Imagine you have a `HelloService` and it's implementation and you are only missing the Appliciation. You could prepare:

@[lagom-application](code/ServiceImplementation.scala)

That is a simple enough Application but it's not complete for running. It extends [`LagomApplication`](api/com/lightbend/lagom/scaladsl/server/LagomApplication.html) so it has all the basic bits and pieces but you need to specify at least a [[Service Locator|ServiceLocator]] for each environment Lagom supports:

@[lagom-loader](code/ServiceImplementation.scala)

Once the `Application` was ready the `ApplicationLoader` is responsible to instantiate and provide the final bits of the cake. See how the `ApplicationLoader` defines two methods: `loadDevMode()` and `load()` so you can mix-in different ServiceLocators.

This is a brief introduction to Lagom Application cake, there's more information in [[Wiring together a Lagom application|ServiceImplementation#Wiring-together-a-Lagom-application]] section.

## Components

This is a list of available `Components` you will need to build your application cake.

[LagomServerComponents](api/com/lightbend/lagom/scaladsl/server/LagomServerComponents.html)

 *  a main `Component` for any Lagom Service and is already mixed into [`LagomApplication`](api/com/lightbend/lagom/scaladsl/server/LagomApplication.html), you won't need to use it explicitly. Includes [LagomServiceClientComponents](api/com/lightbend/lagom/scaladsl/client/LagomServiceClientComponents.html). See [[Wiring together a Lagom application|ServiceImplementation#Wiring-together-a-Lagom-application]].

[LagomServiceClientComponents](api/com/lightbend/lagom/scaladsl/client/LagomServiceClientComponents.html)

 * a main `Component` for any Lagom Service or application consuming Lagom Services. It is already mixed into [`LagomClientApplication`](api/com/lightbend/lagom/scaladsl/client/LagomClientApplication.html), you won't need to use it explicitly. See [[Binding a service client|ServiceClients#Binding-a-service-client]].

[MetricsServiceComponents](api/com/lightbend/lagom/scaladsl/server/status/MetricsServiceComponents.html)

 * adds a `MetricsService` to your service so you can remotely track the status of the CircuitBreakers on your service. Using this only makes sense when you Application is consuming other services (hence using remote calls protected with Circuit Breakers)

[LagomKafkaClientComponents](api/com/lightbend/lagom/scaladsl/broker/kafka/LagomKafkaClientComponents.html)

 * provide a Kafka implementation for the Broker API so that you Service can subscribe to a Kafka topic. See[[KafkaClient|KafkaClient#Subscriber-only-Services]].

[LagomKafkaComponents](api/com/lightbend/lagom/scaladsl/broker/kafka/LagomKafkaComponents.html)

 * provides a Kafka implementation for the Broker API so that you Service can `publish` to a Kafka topic. This component includes `LagomKafkaClientComponents` so if you mix this one in, you will be able to `publish` and `subscribe`. See [[Kafka Client|KafkaClient#Dependency]].

[TestTopicComponents](api/com/lightbend/lagom/scaladsl/testkit/TestTopicComponents.html)

 * provides stubbing tools to test services that use the Broker API. This is meant to be used only on Applications you build on your tests. See [[Testing publish|MessageBrokerTesting#Testing-publish]]

[ClusterComponents](api/com/lightbend/lagom/scaladsl/cluster/ClusterComponents.html)

 * registers the node to the Akka Cluster. Tha Akka Cluster is required by Pub-Sub and Persistent Entity support and you will rarely need to use it explicitly.

[PubSubComponents](api/com/lightbend/lagom/scaladsl/pubsub/PubSubComponents.html)

 * provides [[Publish-Subscribe|PubSub#Publish-Subscribe]]. This requires `ClusterComponents`.

[CassandraPersistenceComponents](api/com/lightbend/lagom/scaladsl/persistence/cassandra/CassandraPersistenceComponents.html)

 * provides both Read-Side and Write-Side components for Cassandra-backed CQRS. It provides `ReadSideCassandraPersistenceComponents`
  and `WriteSideCassandraPersistenceComponents` which you might want to use in isolation. See [[PersistentEntityCassandra]] for more info.

[JdbcPersistenceComponents](api/com/lightbend/lagom/scaladsl/persistence/jdbc/JdbcPersistenceComponents.html)

 * provides both Read-Side and Write-Side components for Cassandra-backed CQRS. It provides `ReadSideJdbcPersistenceComponents`
  and `WriteSideJdbcPersistenceComponents` which you might want to use in isolation. See [[PersistentEntityRDBMS]].

[LagomDevModeServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/devmode/LagomDevModeServiceLocatorComponents.html)

 * provides the dev mode service locator. This is meant to be used by Lagom Services and other applications such as Play Apps that want to interact with Lagom Services in Dev Mode. See the [scaladocs](api/com/lightbend/lagom/scaladsl/devmode/LagomDevModeServiceLocatorComponents.html) for more details.

[LagomDevModeComponents](api/com/lightbend/lagom/scaladsl/devmode/LagomDevModeComponents.html)

 * provides the dev mode service locator and registers the services with it in dev mode. See [[Wiring together a Lagom application|ServiceImplementation#Wiring-together-a-Lagom-application]].

[StaticServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/client/StaticServiceLocatorComponents.html)

 * provides a Service Locator that always resolves the same URI for a given Service name.

[ConfigurationServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocatorComponents.html)

 * provides a Service Locator based on `application.conf` files. See [[deploying using static service locations|ProductionOverview#Deploying-using-static-service-locations]].

[RoundRobinServiceLocatorComponents](api/com/lightbend/lagom/scaladsl/client/RoundRobinServiceLocatorComponents.html)

 * provides a Service Locator that applies a Round Robin over the passed in sequence of URI.

[CircuitBreakerComponents](api/com/lightbend/lagom/scaladsl/client/CircuitBreakerComponents.html)

 * implementors of a Service Locator will need to extend this to reuse the Circuit Breaker config provided by Lagom.



You can mix in `Components` from other frameworks or libraries, for example:

 * [ConductRApplicationComponents](https://github.com/typesafehub/conductr-lib/blob/master/lagom1-scala-conductr-bundle-lib/src/main/scala/com/typesafe/conductr/bundlelib/lagom/scaladsl/ConductRApplicationComponents.scala)
 * [AhcWSComponents](https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.libs.ws.ahc.AhcWSComponents): provides Async HTTP communication
 * [DBComponents](https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.db.DBComponents)
 * [HikariCPComponents](https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.db.HikariCPComponents)

You can find a complete list of inherited `Components` in [Play docs](https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.db.HikariCPComponents).
