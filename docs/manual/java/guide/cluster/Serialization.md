# Serialization

Out of the box, Lagom will use JSON for request and response message format for the external API of the service, using Jackson to serialize and deserialize messages. The messages that are sent within the cluster of the service must also be serializable and so must the events that are stored by [[Persistent Entities|PersistentEntity]]. We recommend JSON for these as well and Lagom makes it easy to add Jackson serialization support to such classes.

Do not depend on Java serialization for production deployments. It is inefficient both in serialization size and speed. It is very difficult to evolve the classes when using Java serialization, which is especially important for the persistent state and events, since you must be able to deserialize old objects that were stored.

## Enabling JSON Serialization

To enable JSON serialization for a class you need to implement the [Jsonable](api/index.html?com/lightbend/lagom/serialization/Jsonable.html) marker interface.

@[jsonable](code/docs/home/serialization/AbstractUser.java)

Note that we're using the [[Immutables|Immutable]] library here, so this will generate an immutable `User` class. This is the reason for adding the `@JsonDeserialize` annotation.

### Jackson Modules

The enabled Jackson modules are listed in the [Akka documentation](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html#jackson-modules), and additionally following Jackson modules are enabled by default:

@[jackson-modules](../../../../../jackson/src/main/resources/reference.conf)

You can amend the configuration `akka.serialization.jackson.jackson-modules` to enable other modules.

The [ParameterNamesModule](https://github.com/FasterXML/jackson-modules-java8/tree/master/parameter-names) requires that the `-parameters` Java compiler option is enabled.

The section [[Immutable Objects|Immutable]] contains more examples of classes that are `Jsonable`.

You can use the [PersistentEntityTestDriver](api/index.html?com/lightbend/lagom/javadsl/testkit/PersistentEntityTestDriver.html) that is described in the [[Persistent Entity Unit Testing|PersistentEntity#Unit-Testing]] section to verify that all commands, events, replies and state are serializable.

### Compression

Compression, as described here, is only used for persistent events, persistent snapshots and remote messages with the service cluster. It is not used for messages that are serialized in the external API of the service.

JSON can be rather verbose and for large messages it can be beneficial to enable compression. That is done by using the [CompressedJsonable](api/index.html?com/lightbend/lagom/serialization/CompressedJsonable.html) instead of the `Jsonable` marker interface.

@[compressed-jsonable](code/docs/home/serialization/AbstractAuthor.java)

The serializer will by default only compress messages that are larger than 32 Kb. This threshold can be changed with configuration property `akka.serialization.jackson.jackson-json-gzip.compress-larger-than`.

## Schema Evolution

When working on long running projects using [[Persistence|PersistentEntity]], or any kind of Event Sourcing, schema evolution becomes an important aspects of developing your application. The requirements as well as our own understanding of the business domain may (and will) change over time.

Lagom provides a way to perform transformations of the JSON tree model during deserialization.

We will look at a few scenarios of how the classes may be evolved.

### Remove Field

Removing a field can be done without any migration code. The Jackson JSON serializer will ignore properties that does not exist in the class.

### Add Field

Adding an optional field can be done without any migration code. The default value will be `Optional.empty`.

Old class:

@[add-optional](code/docs/home/serialization/v1/AbstractItemAdded.java)

New class with a new optional `discount` property and a new `note` field with default value:

@[add-optional](code/docs/home/serialization/v2a/AbstractItemAdded.java)

Let's say we want to have a mandatory `discount` property without default value instead:

@[add-mandatory](code/docs/home/serialization/v2b/AbstractItemAdded.java)

To add a new mandatory field we have to use a JSON migration class and set the default value in the migration code, which extends the `JacksonMigration`.

This is how a migration class would look like for adding a `discount` field:

@[add](code/docs/home/serialization/v2b/ItemAddedMigration.java)

Override the `currentVersion` method to define the version number of the current (latest) version. The first version, when no migration was used, is always 1. Increase this version number whenever you perform a change that is not backwards compatible without migration code.

Implement the transformation of the old JSON structure to the new JSON structure in the `transform` method. The [JsonNode](https://fasterxml.github.io/jackson-databind/javadoc/2.6/com/fasterxml/jackson/databind/JsonNode.html) is mutable so you can add and remove fields, or change values. Note that you have to cast to specific sub-classes such as [ObjectNode](https://fasterxml.github.io/jackson-databind/javadoc/2.6/com/fasterxml/jackson/databind/node/ObjectNode.html) and [ArrayNode](https://fasterxml.github.io/jackson-databind/javadoc/2.6/com/fasterxml/jackson/databind/node/ArrayNode.html) to get access to mutators.

The migration class must be defined in configuration file:

    akka.serialization.jackson.migrations {
      "com.myservice.event.ItemAdded" = "com.myservice.event.ItemAddedMigration"
    }

### Rename Field

Let's say that we want to rename the `productId` field to `itemId` in the previous example.

@[rename](code/docs/home/serialization/v2c/AbstractItemAdded.java)

The migration code would look like:

@[rename](code/docs/home/serialization/v2c/ItemAddedMigration.java)

### Structural Changes

In a similar way we can do arbitrary structural changes.

Old class:

@[structural](code/docs/home/serialization/v1/AbstractCustomer.java)

New class:

@[structural](code/docs/home/serialization/v2a/AbstractCustomer.java)

with the `Address` class:

@[structural](code/docs/home/serialization/v2a/AbstractAddress.java)

The migration code would look like:

@[structural](code/docs/home/serialization/v2a/CustomerMigration.java)

### Rename Class

It is also possible to rename the class. For example, let's rename `OrderAdded` to `OrderPlaced`.

Old class:

@[rename-class](code/docs/home/serialization/v1/AbstractOrderAdded.java)

New class:

@[rename-class](code/docs/home/serialization/v2d/AbstractOrderPlaced.java)

The migration code would look like:

@[rename-class](code/docs/home/serialization/v2d/OrderPlacedMigration.java)

Note the override of the `transformClassName` method to define the new class name.

That type of migration must be configured with the old class name as key. The actual class can be removed.

    akka.serialization.jackson.migrations {
      "com.myservice.event.OrderAdded" = "com.myservice.event.OrderPlacedMigration"
    }
