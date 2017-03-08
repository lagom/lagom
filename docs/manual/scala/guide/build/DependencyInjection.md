# Dependency Injection in Lagom

When building services in Lagom, your code will have dependencies on Lagom APIs and other services that need to be satisfied by concrete implementations at runtime. Often, the specific implementations will vary between development, test and production environments, so it's important not to couple your code tightly to a concrete implementation class. A class can declare constructor parameters with the abstract types of its dependencies --- usually represented by traits in Scala --- allowing the concrete implementations to be provided when the class is constructed. This pattern is called "dependency injection" and is fundamental to the way Lagom applications are assembled. Read "Dependency Injection in Scala using MacWire" for more background on this pattern and its benefits.

Your service's dependencies will have their own dependencies on other APIs in turn. Taken all together, these form a dependency graph that must be constructed when your application starts up. This process is called "wiring" your application, and is performed by creating a subclass of LagomApplication that contains initialization code.

It is common to have clusters of interdependent classes that together form a larger logical component. It can be useful to modularize wiring code in a way that reflects these groupings. You can do so in Scala by defining a "component" trait that includes a combination of lazy val declarations that instantiate concrete implementations of interfaces that the component provides. Some of their constructor parameters may be declared as abstract methods, indicating a dependency that must be provided by another component or your application itself. Your application can then extend multiple components to mix them together into a fully assembled dependency graph. If you don't fulfill all of the declared requirements for the components you include in your service, it will not compile.

In "[Dependency Injection in Scala using MacWire](http://di-in-scala.github.io/#modules)" this method of mixing components together to form an application is referred to as the "thin cake pattern".

Let's see an example with actual Lagom Scala API code. Imagine you have a `HelloService` (and a `HelloServiceImpl`) and you are building your Application cake. You could prepare:

@[lagom-application](code/ServiceImplementation.scala)

That is a simple enough Application but if you pay close attention you'll notice it's is an `abstract class`. The `HelloApplication` builds all the basics for the application to be run but it's not complete. It extends [`LagomApplication`](api/com/lightbend/lagom/scaladsl/server/LagomApplication.html) so it needs a `serviceLocator: ServiceLocator`, a `lagomService: LagomServer` and a `wcClient: WSClient`. We provide the `wcClient: WSClient` mixing in `AhcWSComponents` and we provide `lagomService: LagomServer` programmatically. We still need a `serviceLocator: ServiceLocator` but we need to specify a different [[Service Locator|ServiceLocator]] for each environment we want our app to run in (development environment or production environment):

@[lagom-loader](code/ServiceImplementation.scala)

In the `ApplicationLoader` depending on the runtime environment we will complete the cake in a slightly different way. See how the `ApplicationLoader` defines two methods: `loadDevMode()` and `load()` so you can mix-in different ServiceLocators. In `loadDevMode()` we mix in `LagomDevModeComponents` which provide a Service Locator for Lagom's development mode environment and will also add a feature into our service to self register when the service boots up.

This is a brief introduction to Lagom Application cake, there's more information in [[Wiring together a Lagom application|ServiceImplementation#Wiring-together-a-Lagom-application]].

## Defining your own components

To create a complex Application (one that uses Persistence, Clustering, the Broker API, etc...) you will need to mix in many Components. It is a good choice to create small custom traits that mix in some of those Components and build the Application by mixing in your small custom traits. That will let you test parts of the complete Application in isolation.

Imagine your Service consumes messages from a Broker topic where `Orders` are notified. Then your service stores that info into a database and does some processing with a final step of invoking a third party endpoint. If you only wanted to test the consuming of messages and proper storage you could create an `OrderConsumingComponent` trait and mix in `LagomServiceClientComponents` and `CassandraPersistenceComponents` so that you could consume the messages and store them. On your test you could extend your `OrderConsumingComponent` with `TestTopicComponents` that provides a mocked up broker so you didn't need to start a broker to run the tests. Finally, on your Application you would mix in the tested `OrderConsumingComponent` and `LagomKafkaClientComponents`.

Lagom provides Components so you can build your Application with each feature Lagom provides. These categories group the components available.

 * [[Service Components|ScalaComponents#Service-Components]]
 * [[Persistence and Cluster Components|ScalaComponents#Persistence-and-Cluster-Components]]
 * [[Broker API Components|ScalaComponents#Broker-API-Components]]
 * [[Service Locator Components|ScalaComponents#Service-Locator-Components]]
 * [[Third party Components|ScalaComponents#Third-party-Components]]
