# Lagom 1.2 Migration Guide

This guide explains how to migrate from a Lagom 1.1 application to Lagom 1.2.

## Build changes

### Maven

If you're using a `lagom.version` property in the `properties` section of your root `pom.xml`, then simply update that to `1.2.0`. Otherwise, you'll need to go through every place that a Lagom dependency, including plugins, is used, and set the version there.

If you were using Lagom persistence, the Cassandra support has been split out into its own module. You'll need to update your dependencies to point to `lagom-javadsl-persistence-cassandra`, instead of `lagom-javadsl-persistence`.

### sbt

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.2.0")
```

If you were using Lagom persistence, the Cassandra support has been split out into its own module. You'll need to update your dependencies to point to `lagomJavadslPersistenceCassandra`, instead of `lagomJavadslPersistence`.

## Migrate read-sides to the new API

Lagom now provides a new API for writing read-sides. The features this API provides includes:

* Automatic offset handling - it is no longer necessary to manually manage your offset table yourself.
* Sharded read-sides

The old `CassandraReadSideProcessor` API is still available, but is deprecated. There is no immediate need to switch to the new API when you migrate to Lagom 1.2, however, it is advisable to make the switch as the old API may be removed in a future version of Lagom.

Before migrating, we recommend that you read the documentation. You should read both [[Persistent Read-Side|ReadSide]], as well as [[Cassandra Read-Side support|ReadSideCassandra]].  Once you have done that, the following tasks need to be done to the processor class itself:

* Modify your read side processor to implement [`ReadSideProcessor`](api/index.html?com/lightbend/lagom/javadsl/persistence/ReadSideProcessor.html) instead of [`CassandraReadSideProcessor`](api/index.html?com/lightbend/lagom/javadsl/persistence/cassandra/CassandraReadSideProcessor.html).
* Ensure your read side processor has both [`CassandraReadSide`](api/index.html?com/lightbend/lagom/javadsl/persistence/cassandra/CassandraReadSide.html) and [`CassandraSession`](api/index.html?com/lightbend/lagom/javadsl/persistence/cassandra/CassandraSession.html) injected into its constructor.
* Replace the `aggregateTag` method with the new `aggregateTags` method, and instead of returning your single tag, wrap it in a singleton list using `TreePVector.singleton()`.
* Implement the `buildHandler` method by invoking the `CassandraReadSide.builder` method, passing an ID for this read-side.
* Remove all code that handles offsets.
* Split the `prepare` method into parts that should be done once globally across all nodes (such as creating tables), and parts that should be done once per processor (such as preparing statements), and pass these to the `setGlobalPrepare` and `setPrepare` callbacks on the read side builder in `buildHandler`.
* Replace the defineEventHandlers method with defining event handlers in the `buildHandler` method.

When you register the read-side, instead of invoking `CassandraReadSide.register`, you should invoke `ReadSide.register`. This will typically mean having [`ReadSide`](api/index.html?com/lightbend/lagom/javadsl/persistence/ReadSide.html) instead of [`CassandraReadSide`](api/index.html?com/lightbend/lagom/javadsl/persistence/cassandra/CassandraReadSide.html) injected into the component that registers the read-side processor.

Finally, you'll need to bootstrap the new Cassandra offset table with the old offset before you deploy, otherwise your read-side processor will re-process all the events for the entire history when you deploy your upgraded service. To do this, create the table:

```sql
CREATE TABLE IF NOT EXISTS offsetStore (
    eventProcessorId text,
    tag text,
    timeUuidOffset timeuuid,
    sequenceOffset bigint,
    PRIMARY KEY (eventProcessorId, tag)
)
```

Then, run the following statement to update it:

```sql
INSERT INTO offsetStore (eventProcessorId, tag, timeUuidOffset, sequencOffset)
VALUES ('<your-event-processor-id>', '<your-tag>', '<uuid>', null)
```

Where `<your-event-processor-id>` is the ID that you passed to `CassandraReadSide.builder()` in your read side processor, `<your-tag>` is the name of your aggregate tag (by default, if not explicitly specified, this is the fully qualify class name of your event interface), and `<uuid>` is the UUID in your manual offset store.

If the updates on your read-side are idempotent, then this task can be done at any time before deployment - reprocessing a few events shouldn't be a problem. Otherwise, you will need to ensure that you do this while your service is not running, to prevent any events from being reprocessed. An alternative to this would be to drop your read-side tables before upgrading, and so start processing events from the start.

## Testkit changes

The testkit has been modified such that Cassandra and clustering are disabled by default, whereas previously they were enabled by default. This is to accommodate for the fact that Lagom now has multiple persistence backends, not just Cassandra.

If you have tests that depend on Cassandra, you will need to update them to enable Cassandra, using:

```java
defaultSetup().withCassandra(true)
```

