# Testing Services

## Running tests

You can run tests from the sbt console.

* To run all tests, run `test`.
* To run only one test class, run `testOnly` followed by the name of the class i.e. `testOnly my.namespace.MyTest`.
* To run only the tests that have failed, run `testQuick`.
* To run tests continually, run a command with a tilde in front, i.e. `~testQuick`.

## JUnit

The recommended test framework for Lagom is [JUnit 4](https://junit.org/junit4/)

@[test](code/docs/services/test/SimpleTest.java)

## Dependency

To use this feature add the following in your project's build.

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-testkit_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
    <scope>test</scope>
</dependency>
```

In sbt:

@[testkit-dependency](code/build-service.sbt)

When using Cassandra the tests must be forked, which is enabled by adding the following in your project's build:

@[fork](code/build-service.sbt)

## How to test one service

Lagom provides support for writing functional tests for one service in isolation. The service is running in a server and in the test you can interact with it using its service client, i.e. calls to the service API. These utilities are defined in [ServiceTest](api/index.html?com/lightbend/lagom/javadsl/testkit/ServiceTest.html).

@[test](code/docs/services/test/HelloServiceTest.java)

Dependencies to other services must be replaced by stub or mock implementations by overriding the bindings of the `GuiceApplicationBuilder` in the `Setup`. If we are writing a test for the `HelloService` and that has a dependency to a `GreetingService` we must create an implementation of the `GreetingService` that can be used for the test without running the real `GreetingService`. Something like this:

@[stub](code/docs/services/test/StubDependencies.java)

Note how the dependency is overridden when constructing the test `Setup` object, which then can be used as parameter to the `withServer` method instead of the `defaultSetup()` in the above `HelloServiceTest`.

The server is by default running with [[pubsub|PubSub]], [[cluster|Cluster]] and [[persistence|PersistentEntity]] features disabled. You may want to enable cluster in the `Setup`:

@[enable-cluster](code/docs/services/test/EnablePersistenceCluster.java)

If your service needs [[persistence|PersistentEntity]] you will need to enable it explicitly. This can be done by enabling Cassandra or JDBC, depending on which kind of persistence is used by your service. In any case, Lagom persistence requires clustering, so when enabling one or another, cluster will also be enabled automatically.

You can't enable both (Cassandra and JDBC) at the same time for testing, which could be a problem if you are mixing persistence for write and read side. If you are using Cassandra for write-side and JDBC for read-side, just enable Cassandra.

To enable Cassandra Persistence:

@[enable-cassandra](code/docs/services/test/EnablePersistenceCassandra.java)

To enable JDBC Persistence:

@[enable-jdbc](code/docs/services/test/EnablePersistenceJdbc.java)

There's no way to explicitly enable or disable [[pubsub|PubSub]]. When cluster is enabled (either explicitly or transitively via enabling Cassandra or JDBC), pubsub will be available.

There are two different styles that can be used when writing the tests. It is most convenient to use `withServer` as illustrated in the above `HelloServiceTest`. It automatically starts and stops the server before and after the given lambda.

When your tests have several test methods, and especially when using persistence, it is faster to only start the server once in a static method annotated with `@BeforeClass` and stop it in a method annotated with `@AfterClass`.

@[test](code/docs/services/test/AdvancedHelloServiceTest.java)

## How to use TLS on tests

To open an SSL port on the `TestServer` used in your tests, you may enable SSL support using `withSsl`:

```java
Setup.defaultSetup.withSsl()
```

Enabling SSL will automatically open a new random port and provide an `javax.net.ssl.SSLContext` on the TestServer. Lagom doesn't provide any client factory that allows sending requests to the HTTPS port at the moment. You should create an HTTP client using Play-WS, Akka-HTTP or Akka-gRPC. Then, use the `httpsPort` and the `sslContext` provided by the `testServer` instance to send the request. Note that the `SSLContext` provided is built by Lagom's testkit to trust the `testServer` certificates. Finally, because the server certificate is issued for `CN=localhost` you will have to make sure that's the `authority` on the requests you generate, otherwise the server may decline and fail the request. At the moment it is not possible to setup the test server with different SSL Certificates.


@[tls-test-service](../../../../../testkit/javadsl/src/test/java/com/lightbend/lagom/javadsl/testkit/TestOverTlsTest.java)


## How to test several services

Lagom will provide support for writing integration tests that involve several interacting services. This feature is [not yet implemented](https://github.com/lagom/lagom/issues/38).

## How to test streamed request/response

Let's say we have a service that have streaming request and/or response parameters. For example an `EchoService` like this:

@[echo-service](code/docs/services/test/EchoService.java)

When writing tests for that the [Akka Streams TestKit](https://doc.akka.io/docs/akka/2.6/stream/stream-testkit.html?language=java#streams-testkit) is very useful. We use the Streams TestKit together with the Lagom `ServiceTest` utilities:

@[test](code/docs/services/test/EchoServiceTest.java)

Read more about it in the documentation of the [Akka Streams TestKit](https://doc.akka.io/docs/akka/2.6/stream/stream-testkit.html?language=java#streams-testkit).

## How to test broker publishing and consuming

The section on [[Message Broker Testing|MessageBrokerTesting]] is related to Testing Services but is specific to testing the production and consumption via Brokers.

## How to test PersistentEntity

[[Persistent Entities|PersistentEntity#Unit-Testing]] can be used in the service tests described above. In addition to that you should write unit tests using the [PersistentEntityTestDriver](api/index.html?com/lightbend/lagom/javadsl/testkit/PersistentEntityTestDriver.html), which will run the `PersistentEntity` without using a database.

This is described in the documentation of [[Persistent Entity|PersistentEntity#Unit-Testing]]
