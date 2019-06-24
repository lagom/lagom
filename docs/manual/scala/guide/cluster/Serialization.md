# Serialization

Out of the box, Lagom will use JSON for request and response message format for the external API of the service, using Play JSON to serialize and deserialize messages. The messages that are sent within the cluster of the service must also be serializable and so must the events that are stored by [[Persistent Entities|PersistentEntity]]. We recommend JSON for these as well and Lagom makes it easy to add Play JSON serialization support to such classes.

Do not depend on Java serialization for production deployments. It is inefficient both in serialization size and speed. It is very difficult to evolve the classes when using Java serialization, which is especially important for the persistent state and events, since you must be able to deserialize old objects that were stored.

Runtime overhead is avoided by not basing the serialization on reflection. Transformations to and from JSON are defined either manually or by using a built in macro - essentially doing what reflection would do, but at compile time instead of during runtime. This comes with one caveat, each top level class that can be serialized needs an explicit serializer defined. To use the Play JSON support in Lagom you need to provide such serializers for each type.

The Play JSON abstraction for serializing and deserializing a class into JSON is the [Format](https://www.playframework.com/documentation/2.6.x/api/scala/index.html#play.api.libs.json.Format) which in turn is a combination of [Reads](https://www.playframework.com/documentation/2.6.x/api/scala/index.html#play.api.libs.json.Reads) and [Writes](https://www.playframework.com/documentation/2.6.x/api/scala/index.html#play.api.libs.json.Writes). The library parses JSON into a JSON tree model, which is what the `Format`s work with.


## Enabling JSON Serialization

To enable JSON Serialization there are three steps you need to follow.

The first step is to define your [Format](https://www.playframework.com/documentation/2.6.x/api/scala/play/api/libs/json/Format.html) for each class that is to be serialized, this can be done using [automated mapping](#Automated-mapping) or [manual mapping](#Manual-mapping).

@[format](code/docs/home/scaladsl/serialization/AddPost.scala)

Best practice is to define the `Format` as an implicit in the classes companion object, so that it can be found by implicit resolution.

The second step is to implement [JsonSerializerRegistry](api/com/lightbend/lagom/scaladsl/playjson/JsonSerializerRegistry.html) and have all the service formats returned from its `serializers` method.

@[registry](code/docs/home/scaladsl/serialization/Registry.scala)

Having done that, you can provide the serializer registry by overriding the `jsonSerializerRegistry` component method in your application cake, for example:

@[application-cake](code/docs/home/scaladsl/serialization/Registry.scala)

If you need to use the registry outside of a Lagom application, for example, in tests, this can be done by customising the creation of the actor system, for example:

@[create-actor-system](code/docs/home/scaladsl/serialization/Registry.scala)

## Compression

Compression, as described here, is only used for persistent events, persistent snapshots and remote messages with the service cluster. It is not used for messages that are serialized in the external API of the service.

JSON can be rather verbose and for large messages it can be beneficial to enable compression. That is done by using the [`JsonSerializer.compressed[T]`](api/com/lightbend/lagom/scaladsl/playjson/JsonSerializer$.html) builder method instead of the `JsonSerializer.apply[T]` (as shown in the example snippet above):

@[registry-compressed](code/docs/home/scaladsl/serialization/RegistryWithCompression.scala)

The serializer will by default only compress messages that are larger than 32Kb. This threshold can be changed with configuration property:

@[compress-larger-than](../../../../../play-json/src/main/resources/reference.conf)

## Automated mapping

The [Json.format\[MyClass\]](https://www.playframework.com/documentation/2.6.x/api/scala/index.html#play.api.libs.json.Json$@format[A]:play.api.libs.json.OFormat[A]) macro will inspect a `case class` for what fields it contains and produce a `Format` that uses the field names and types of the class in the resulting JSON.

The macro allows for defining formats based on the exact structure of the class which is handy and avoids spending development time on explicitly defining the format, on the other hand it tightly couples the structure of the JSON with the structure of the class so that a refactoring of the class unexpectedly leads to the format being unable to read JSON that was serialized before the change. There are tools in place to deal with this (see [schema evolution](#Schema-Evolution)) but care must be taken.

If the class contains fields of complex types, it pulls those in from `implicit` marked `Format`s in the scope. This means that you must provide such implicit formats for all the complex types used inside a class before calling the macro.

@[complexMembers](code/docs/home/scaladsl/serialization/AddComment.scala)

## Manual mapping

Defining a `Format` can be done in several ways using the Play JSON APIs, either using [JSON Combinators](https://playframework.com/documentation/2.6.x/ScalaJsonCombinators#Format) or by manually implementing functions that turn a `JsValue` into a `JsSuccess(T)` or a `JsFailure()`.

@[manualMapping](code/docs/home/scaladsl/serialization/AddOrder.scala)

## Special mapping considerations

### Mapping options

The automatic mapping will handle `Option` fields, for manual mapping of optional fields you can use `(JsPath \ "optionalField").formatNullable[A]`. This will treat missing fields as `None` allowing for adding of new fields without providing an explicit schema migration step.

### Mapping singletons

For toplevel singletons (Scala `object`s) you can use [com.lightbend.lagom.scaladsl.playjson.Serializers.emptySingletonFormat](api/index.html#com/lightbend/lagom/scaladsl/playjson/Serializers@emptySingletonFormat) to get a `Format` that outputs empty JSON (as the type is also encoded along side the data).

@[singleton](code/docs/home/scaladsl/serialization/AddOrder.scala)


### Mapping hierarchies

When mapping a hierarchy of types, for example an ADT, or a trait or abstract class you will need to provide a `Format` for the supertype, that based on some information in the JSON decides which subtype to deserialize.

@[hierarchy](code/docs/home/scaladsl/serialization/AddOrder.scala)


## Schema Evolution

When working on long running projects using [[Persistence|PersistentEntity]], or any kind of Event Sourcing, schema evolution becomes an important aspect of developing your application. The requirements as well as our own understanding of the business domain may (and will) change over time.

Lagom provides a way to perform transformations of the JSON tree model during deserialization. To do those transformations you can either modify the json imperatively or use the [Play JSON transformers](https://www.playframework.com/documentation/2.6.x/ScalaJsonTransformers)

We will look at a few scenarios of how the classes may be evolved.

### Remove Field

Removing a field can be done without any migration code. Both manual and automatic mappings will ignore properties that does not exist in the class.

### Add Field

Adding an optional field can be done without any migration code if automated mapping is used or manual mapping is used and you have made sure a missing field is read as a `None` by your format (see [mapping options](#Mapping-options)).

Old class:

@[add-optional](code/docs/home/scaladsl/serialization/v1/ItemAdded.scala)

New class with a new optional `discount` property:

@[add-optional](code/docs/home/scaladsl/serialization/v2a/ItemAdded.scala)

Let's say we want to have a mandatory `discount` property without default value instead:

@[add-mandatory](code/docs/home/scaladsl/serialization/v2b/ItemAdded.scala)

To add a new mandatory field we have to use a JSON migration adding a default value to the JSON

This is how a migration logic would look like for adding a `discount` field using imperative code:

@[imperative-migration](code/docs/home/scaladsl/serialization/v2b/ItemAdded.scala)

Create a concrete subclass of [JsonMigration](api/com/lightbend/lagom/scaladsl/playjson/JsonMigration.html) handing it the current version of the schema as a parameter, then implement the transformation logic on the `JsObject` in the `transform` method when an older `fromVersion` is passed in.

Then provide your `JsonMigration` together with the classname of the class that it migrates in the `migrations` map from your `JsonSerializerRegistry`.

Alternatively you can use the [Play JSON transformers](https://www.playframework.com/documentation/2.6.x/ScalaJsonTransformers) API which is more concise but arguably has a much higher threshold to learn.

@[transformer-migration](code/docs/home/scaladsl/serialization/v2b/ItemAdded.scala)

In this case we give the `JsonMigrations.transform` method the type it is for, and a sorted map of transformations that has happened leading up to the current version of the schema.


### Rename Field

Let's say that we want to rename the `productId` field to `itemId` in the previous example.

@[rename](code/docs/home/scaladsl/serialization/v2c/ItemAdded.scala)

The imperative migration code would look like:

@[imperative-migration](code/docs/home/scaladsl/serialization/v2c/ItemAdded.scala)

And alternatively the transformer based migration:

@[transformer-migration](code/docs/home/scaladsl/serialization/v2c/ItemAdded.scala)

### Structural Changes

In a similar way we can do arbitrary structural changes.

Old class:

@[structural](code/docs/home/scaladsl/serialization/v1/Customer.scala)

New classes:

@[structural](code/docs/home/scaladsl/serialization/v2a/Customer.scala)

The migration code could look like:

@[structural-migration](code/docs/home/scaladsl/serialization/v2a/Customer.scala)

### Rename Class

It is also possible to rename the class. For example, let's rename `OrderAdded` to `OrderPlaced`.

Old class:

@[rename-class](code/docs/home/scaladsl/serialization/v1/OrderAdded.scala)

New class:

@[rename-class](code/docs/home/scaladsl/serialization/v2a/OrderPlaced.scala)

The migration code would look like:

@[rename-class-migration](code/docs/home/scaladsl/serialization/v2a/OrderPlaced.scala)

When a class has both been renamed and had other changes over time the name change is added separately as in the example and the transformations are defined for the new class name in the migrations map. The Lagom serialization logic will first look for name changes, and then use the changed name to resolve any schema migrations that will be done using the changed name.
