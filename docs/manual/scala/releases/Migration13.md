# Lagom 1.3 migration guide

This guide gives a brief description of the steps required to migrate a Lagom 1.2 Java system to a Lagom 1.3 Scala system.

## Build changes

1. Upgrade the Lagom plugin version in `project/plugins.sbt` to `1.3.2`.
1. If using ConductR with sbt, you should upgrade the ConductR sbt plugin to at least `2.3.4`.
1. Replace `LagomJava` with `LagomScala`, and any references to `javadsl` components to their `scaladsl` equivalents.

## General code changes

Generally, across your codebase, you will need to do the following:

1. Replace any imports on Lagom `javadsl` APIs with imports on the equivalent `scaladsl` APIs. In most cases, these are named the same thing.
1. Replace any uses of Akka `javadsl` streams with `scaladsl` streams.
1. Replace any uses of `java.util.concurrent.CompletionStage` with `scala.concurrent.Future`. Remember that `scala.concurrent.Future` needs an implicit execution context in scope.

## Lagom Service API changes

The primary change necessary for the Lagom Service API is to declare Play JSON formats for all of your request and response messages. These formats should replace any Jackson annotations on your message classes. These formats are typically best declared as an implicit parameter on your message classes' companion objects, using the Play JSON macros as a convenience, for example:

```scala
import play.api.libs.json._

case class Post(title: String, content: String)

object Post {
  implicit val format: Format[Post] = Json.format
}
```

For more details, see [[the message serializer documentation|ServiceDescriptors#Message-serialization]].

If using custom path parameter serializers, these will need to be passed via implicit parameters, rather than being registered with the service descriptor explicitly.

## Service implementation changes

In the Lagom Java API, service calls get implemented using lambdas.  In the Scala API, service calls get implemented by passing lambdas to the `ServiceCall` and `ServerServiceCall` constructors, for example:

```scala
import com.lightbend.lagom.scaladsl.api.ServiceCall
import scala.concurrent.Future

def sayHello = ServiceCall { name =>
  Future.successful(s"Hello $name!")
}
```

Furthermore, there is no need for `HeaderServiceCall` in Lagom, since the Scala type system is easily able to distinguish between a function that takes a single message argument, and a function that takes a request header and a message argument, so `ServerServiceCall` is used both for service calls that ignore the headers, and service calls that interact with the headers.

## Persistence changes

Persistent entities in Lagom express their command, event and state types using abstract types, rather than type parameters on the `PersistentEntity` class. Lagom's persistent entity Scala behavior builders also make use of partial functions and other Scala features. The full documentation on Scala's persistent entities is [[here|PersistentEntity]].

Like the message serializers for services, serializers for messages sent over Akka remoting, and for the persistent entity events and state, need to be defined explicitly, by default this can be done using Play JSON. Since serializers are supplied explicitly, there is no need for a `Jsonable` interface in the Scala API.  For more details, see the [[serialization documentation|Serialization]].

## Application wiring changes

The Lagom Scala API is designed to be used with compile time dependency injection, not Guice. In general, this means removing all JSR-330 annotations from components, such as `@Inject` and `@Singleton`, and creating an application cake to replace the Guice `Module` that the Java API needs defined. For documentation about how to wire together a Lagom Scala application, see [[Wiring together a Lagom application|DependencyInjection#Wiring-together-a-Lagom-application]].
