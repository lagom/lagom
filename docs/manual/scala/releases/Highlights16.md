# What's new in Lagom 1.6?

This page highlights the new features of Lagom 1.6. If you want to learn about the changes you need to make when you migrate to Lagom 1.6, check out the [[Lagom 1.6 Migration Guide|Migration16]].

## Akka Typed and Akka Persistence Typed

Lagom 1.6 is based on the new Akka 2.6 release and have integrated support for Akka Typed.

As presented in the [announcement for Akka 2.6](https://www.lightbend.com/blog/six-things-architects-should-know-about-akka-2.6), the new Akka Actor APIs (known as Akka Typed) represent a major shift in the Akka ecosystem towards type-safety and more explicit guidance with Actors. We’re happy to bring this to all Lagom users as well.

Lagom includes dependency injection support for typed Actors in Akka 2.6 [through Play](https://www.playframework.com/documentation/2.8.x/AkkaTyped#Integrating-with-Akka-Typed).

While the Lagom Persistence API is still supported and maintained, the [[new Akka Persistence API|UsingAkkaPersistenceTyped]] in Akka 2.6 is now the recommended default for persistence. This provides a more flexible API that gives you more control over some lower-level details, while retaining some of the guided approach that Lagom introduced. Akka Persistence can coexist with existing persistent entities, and the same read-side processor and topic producer APIs fully support both types of entities.

## Jackson serialization

In addition to `play-json` support, Lagom for Scala is now also supporting the Jackson serializer from Akka for intracluster messages (commands, events, replies and snapshots). The Jackson serializer provided by Akka is an improved version of the serializer in the Lagom 1.5's Java API. You can find more information about the Akka Jackson serializer in the [Akka documentation](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html).

Akka Jackson serialization is especially useful when using Akka Persistence Typed API as it allows serializaion of `ActorRef[T]` typically used when encoding command replies.

Out of the box, the default [[`MessageSerializer`'s|MessageSerializers]] for a `Service.Descriptor` are still based on `play-json`. 

## Stop and Resume Projections

Lagom 1.6 has a new API to [[programmaticaly stop and resume projections|Projections]] (Read Side Processors and Topic Producers) allowing users to control when a projection should start, stop or resume.

## Support for Scala 2.13 and Java 11

Lagom 1.6 supports the latest Scala version (2.13), LTS Java version (11) and sbt 1.3, as well as the earlier Scala 2.12 and Java 8 versions.
