# Testing Services

## Running tests

Tests can be run from sbt, or from your IDE. Running tests from your IDE will be specific to your IDE, so we'll focus here on how to run tests from sbt.

* To run all tests, run `test`.
* To run only one test class, run `testOnly` followed by the name of the class i.e. `testOnly com.example.MyTest`. Wildcards are supported, so you can say `testOnly *.MyTest`, for example.
* To run only the tests that cover code that has changed, run `testQuick`.
* To run tests continually, run a command with a tilde in front, i.e. `~testQuick`.

## Testing libraries

You can use any testing framework with Lagom, popular ones include [ScalaTest](http://www.scalatest.org/) and [Specs2](https://etorreborre.github.io/specs2/). If you're not sure which to use, or don't have a preference, we use ScalaTest for testing Lagom itself, and we'll document it here.

In addition to a test framework, Lagom also provides a helper library for testing common Lagom components, called the Lagom testkit.

To use your preferred test framework and the Lagom testkit, you'll need to add them to your library dependencies, like so:

@[test-dependencies](code/testing-services.sbt)

You may want to use ScalaTest in multiple places in your build, so often it's a good idea to create a single `val` to hold and, which means you can just reference that val from each place that you need it, rather than having to retype the group id, artifact id and version each time.  This can be done like this:

@[scala-test-val](code/testing-services.sbt)

Then you can use it in your `libraryDependencies` by simply referring to it:

@[test-dependencies-val](code/testing-services.sbt)

When using Cassandra the tests must be forked, which is enabled by adding the following in your project's build:

@[fork](code/testing-services.sbt)

## How to test one service

Lagom provides support for writing functional tests for one service in isolation. The service is running in a server and in the test you can interact with it using its service client, i.e. calls to the service API. These utilities are defined in [ServiceTest](api/com/lightbend/lagom/scaladsl/testkit/ServiceTest$.html).

Here's what a simple test may look like:

@[hello-service-spec](code/TestingServices.scala)

There are a few points to note about this code:

* The test is using ScalaTest's [asynchronous test support](http://www.scalatest.org/user_guide/async_testing). The actual test itself returns a future, and ScalaTest ensures that that future is handled appropriately.
* `withServer` takes three parameters. The first is a setup parameter, which can be used to configure how the environment should be setup, for example, it can be used to start Cassandra. The second is a constructor for a [LagomApplication](api/com/lightbend/lagom/scaladsl/server/LagomApplication.html), which is where we construct the application, and the third is a block to run that takes the started server and runs the actual test.
* When we construct the `LagomApplication`, we mix in `LocalServiceLocator`. This provides a local service locator which will resolve just the services that our application is running itself, and is how the service client we construct knows where to find our running service.
* In the test callback, we implement a service client, which we can then use to talk to our service.

The spec above will start a server for each test, which is often handy because it guarantees a clean state between each test. Sometimes however starting a server for each test can be prohibitively expensive, especially when databases are involved. In these cases it may be better to share the server between all tests in a suite. To do this, `startServer` can be used instead, invoking `stop` in a after suite callback:

@[hello-service-spec-shared](code/TestingServices.scala)

Dependencies to other services must be replaced by stub or mock implementations by overriding them in your `LagomApplication` constructor callback. If we were writing a test for the `HelloService` and it had a dependency on a `GreetingService` we must create a stub implementation of the `GreetingService` that can be used for the test without running the real greeting service. It might look like this:

@[stub-services](code/TestingServices.scala)

The server is by default running with [[pubsub|PubSub]], [[cluster|Cluster]] and [[persistence|PersistentEntity]] features disabled. You may want to enable clustering in the `Setup`:

@[enable-cluster](code/TestingServices.scala)

If your service needs [[persistence|PersistentEntity]] you will need to enable it explicitly. This can be done by enabling Cassandra or JDBC, depending on which kind of persistence is used by your service. In any case, Lagom persistence requires clustering, so when enabling one or another, cluster will also be enabled automatically.

You can't enable both (Cassandra and JDBC) at the same time for testing, which could be a problem if you are mixing persistence for write and read side. If you are using Cassandra for write-side and JDBC for read-side, just enable Cassandra.

To enable Cassandra Persistence:

@[enable-cassandra](code/TestingServices.scala)

To enable JDBC Persistence:

@[enable-jdbc](code/TestingServices.scala)

There's no way to explicitly enable or disable [[pubsub|PubSub]]. When cluster is enabled (either explicitly or transitively via enabling Cassandra or JDBC), pubsub will be available.


## How to use TLS on tests

To open an SSL port on the `TestServer` used in your tests, you may enable SSL support using `withSsl`:

```java
Setup.defaultSetup.withSsl()
```

Enabling SSL will automatically open a new random port and provide an `javax.net.ssl.SSLContext` on the TestServer. Lagom doesn't provide any client factory that allows sending requests to the HTTPS port at the moment. You should create an HTTP client using Play-WS, Akka-HTTP or Akka-gRPC. Then, use the `httpsPort` and the `sslContext` provided by the `testServer` instance to send the request. Note that the `SSLContext` provided is built by Lagom's testkit to trust the `testServer` certificates. Finally, because the server certificate is issued for `CN=localhost` you will have to make sure that's the `authority` on the requests you generate, otherwise the server may decline and fail the request. At the moment it is not possible to setup the test server with different SSL Certificates.


@[tls-test-service](../../../../../testkit/scaladsl/src/test/scala/com/lightbend/lagom/scaladsl/testkit/TestOverTlsSpec.scala)




## How to test several services

Lagom will provide support for writing integration tests that involve several interacting services. This feature is [not yet implemented](https://github.com/lagom/lagom/issues/38).

## How to test streamed request/response

Let's say we have a service that has streaming request and/or response parameters. For example an `EchoService` like this:

@[echo-service](code/TestingServices.scala)

When writing tests for that the [Akka Streams TestKit](https://doc.akka.io/docs/akka/2.6/stream/stream-testkit.html?language=scala#streams-testkit) is very useful. We use the Streams TestKit together with the Lagom `ServiceTest` utilities:

@[echo-service-spec](code/TestingServices.scala)

Read more about it in the documentation of the [Akka Streams TestKit](https://doc.akka.io/docs/akka/2.6/stream/stream-testkit.html?language=scala#streams-testkit).

## How to test a persistent entity

[[Persistent Entities|PersistentEntity]] can be used in the service tests described above. In addition to that you should write unit tests using the [PersistentEntityTestDriver](api/com/lightbend/lagom/scaladsl/testkit/PersistentEntityTestDriver.html), which will run the `PersistentEntity` without using a database.

This is described in the [[Persistent Entity|PersistentEntity#Unit-Testing]] documentation.
