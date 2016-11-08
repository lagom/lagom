# Testing Services

## Running tests

You can run tests from the Activator console.

* To run all tests, run `test`.
* To run only one test class, run `testOnly` followed by the name of the class i.e. `testOnly my.namespace.MyTest`.
* To run only the tests that have failed, run `testQuick`.
* To run tests continually, run a command with a tilde in front, i.e. `~testQuick`.

## JUnit

The recommended test framework for Lagom is [JUnit](http://junit.org/)

@[test](code/docs/services/test/SimpleTest.java)

## Dependency

To use this feature add the following in your project's build.

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-testkit_2.11</artifactId>
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

The server is by default running with [[persistence|PersistentEntity]], [[pubsub|PubSub]] and [[cluster|Cluster]] features enabled. Cassandra is also started before the test server is started. If your service does not use these features you can disable them in the `Setup`, which will reduce the startup time.

Disable persistence, including Cassandra startup:

@[setup1](code/docs/services/test/DisablePersistence.java)

If cluster is disabled the persistence is also disabled, since cluster is a prerequisite for persistence. Disable cluster and pubsub:

@[setup2](code/docs/services/test/DisablePersistence.java)

There are two different styles that can be used when writing the tests. It is most convenient to use `withServer` as illustrated in the above `HelloServiceTest`. It automatically starts and stops the server before and after the given lambda.

When your test have several test methods, and especially when using persistence, it is faster to only start the server once in a static method annotated with `@BeforeClass` and stop it in a method annotated with `@AfterClass`.

@[test](code/docs/services/test/AdvancedHelloServiceTest.java)

## How to test several services

Lagom will provide support for writing integration tests that involve several interacting services. This feature is [not yet implemented](https://github.com/lagom/lagom/issues/38).

## How to test streamed request/response

Let's say we have a service that have streaming request and/or response parameters. For example an `EchoService` like this:

@[echo-service](code/docs/services/test/EchoService.java)

When writing tests for that the [Akka Streams TestKit](http://doc.akka.io/docs/akka/2.4/java/stream/stream-testkit.html#Streams_TestKit) is very useful. We use the Streams TestKit together with the Lagom `ServiceTest` utilities:

@[test](code/docs/services/test/EchoServiceTest.java)

Read more about it in the documentation of the [Akka Streams TestKit](http://doc.akka.io/docs/akka/2.4/java/stream/stream-testkit.html#Streams_TestKit).

## How to test PersistentEntity

[[Persistent Entities|PersistentEntity]] can be used in the service tests described above. In addition to that you should write unit tests using the [PersistentEntityTestDriver](api/index.html?com/lightbend/lagom/javadsl/testkit/PersistentEntityTestDriver.html), which will run the `PersistentEntity` without using a database.

This is described in the documentation of [[Persistent Entity|PersistentEntity#Unit-Testing]]
