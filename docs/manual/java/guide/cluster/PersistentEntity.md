# Persistent Entity

We recommend reading [[Event Sourcing and CQRS|ES_CQRS]] as a prerequisite to this section.

A `PersistentEntity` has a stable entity identifier, with which it can be accessed from the service implementation or other places. The state of an entity is persistent (durable) using [Event Sourcing](https://msdn.microsoft.com/en-us/library/jj591559.aspx). We represent all state changes as events and those immutable facts are appended to an event log. To recreate the current state of an entity when it is started we replay these events.

A persistent entity corresponds to an [Aggregate Root](https://martinfowler.com/bliki/DDD_Aggregate.html) in Domain-Driven Design terms. Each instance has a stable identifier and for a given id there will only be one instance of the entity. Lagom takes care of distributing those instances across the cluster of the service. If you know the identifier you can send messages, so called commands, to the entity.

The persistent entity is also a transaction boundary. Invariants can be maintained within one entity but not across several entities.

If you are familiar with [JPA](https://en.wikipedia.org/wiki/Java_Persistence_API) it is worth noting that a `PersistentEntity` can be used for similar things as a JPA `@Entity` but several aspects are rather different. For example, a JPA `@Entity` is loaded from the database from wherever it is needed, i.e. there may be many Java object instances with the same entity identifier. In contrast, there is only one instance of `PersistentEntity` with a given identifier. With JPA you typically only store current state and the history of how the state was reached is not captured.

You interact with a `PersistentEntity` by sending command messages to it. Commands are processed sequentially, one at a time, for a specific entity instance. A command may result in state changes that are persisted as events, representing the effect of the command. The current state is not stored for every change, since it can be derived from the events. These events are only ever appended to storage, nothing is ever mutated, which allows for very high transaction rates and efficient replication.

The entities are automatically distributed across the nodes in the cluster of the service. Each entity runs only at one place, and messages can be sent to the entity without requiring the sender to know the location of the entity. If a node is stopped the entities running on that node will be started on another node when a message is sent to it next time. When new nodes are added to the cluster some existing entities are rebalanced to the new nodes to spread the load.

An entity is kept alive, holding its current state in memory, as long as it is used. When it has not been used for a while it will automatically be passivated to free up resources.

When an entity is started it replays the stored events to restore the current state. This can be either the full history of changes or starting from a snapshot which will reduce recovery times.

## Choosing a database

Lagom is compatible with the following databases:

- [Cassandra](https://cassandra.apache.org/)
- [PostgreSQL](https://www.postgresql.org/)
- [MySQL](https://www.mysql.com/)
- [Oracle](https://www.oracle.com/database/index.html)
- [H2](https://www.h2database.com/)
- [Microsoft SQL Server](https://www.microsoft.com/en-us/sql-server/)
- [Couchbase](https://www.couchbase.com/)

For instructions on configuring your project to use Cassandra, see [[Using Cassandra for Persistent Entities|PersistentEntityCassandra]]. If instead you want to use one of the relational databases listed above, see [[Using a Relational Database for Persistent Entities|PersistentEntityRDBMS]] on how to configure your project. If you wish to use Couchbase, proceed to the [Lagom section of the Akka Persistence Couchbase site](https://doc.akka.io/docs/akka-persistence-couchbase/current/lagom-persistent-entity.html) for all the details.

To see how to combine Cassandra for write-side persistence and JPA for a read-side view, see the [Mixed Persistence Service](https://github.com/lagom/lagom-samples/blob/1.6.x/mixed-persistence/mixed-persistence-java-sbt/README.md) sample.

Lagom provides out of the box support for running Cassandra in a development environment - developers do not need to install, configure or manage Cassandra at all themselves when using Lagom, which makes for great developer velocity, and it means gone are the days where developers spend days setting up their development environment before they can start to be productive on a project.

## PersistentEntity Stub

This is what a [PersistentEntity](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntity.html) class looks like before filling in the implementation details:

@[post1](code/docs/home/persistence/Post1.java)

The three type parameters of the extended `PersistentEntity` class define:

* `Command` - the super class/interface of the commands
* `Event` - the super class/interface of the events
* `State` - the class of the state

`initialBehavior` is an abstract method that your concrete subclass must implement. It returns the `Behavior` of the entity. Use `newBehaviorBuilder` to create a mutable builder for defining the behavior. The behavior consists of current state and functions to process incoming commands and persisted events as described in the following sections.

## Command Handlers

The functions that process incoming commands are registered in the `Behavior` using `setCommandHandler` of the `BehaviorBuilder`.

@[command-handler](code/docs/home/persistence/Post2.java)

You should define one command handler for each command class that the entity can receive.

A command handler returns a [Persist](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntity.Persist.html) directive that defines what event or events, if any, to persist. Use the `thenPersist`, `thenPersistAll` or `done` methods of the context that is passed to the command handler function to create the `Persist` directive.

* `thenPersist` will persist one single event
* `thenPersistAll` will persist several events atomically, i.e. all events
  are stored or none of them are stored if there is an error
* `done` no events are to be persisted

External side effects can be performed after successful persist in the `afterPersist` function. In the above example a reply is sent with the `ctx.reply` method.

The command can be validated before persisting state changes. Use `ctx.invalidCommand` or `ctx.commandFailed` to reject an invalid command.

@[validate-command](code/docs/home/persistence/Post2.java)

A `PersistentEntity` may also process commands that do not change application state, such as query commands  or commands that are not valid in the entity's current state (such as a bid placed after the auction closed). Such command handlers are registered using `setReadOnlyCommandHandler` of the `BehaviorBuilder`. Replies are sent with the `reply` method of the context that is passed to the command handler function.

The `setReadOnlyCommandHandler` is simply a convenience function that avoids you having to return no events followed by a side effect.


@[read-only-command-handler](code/docs/home/persistence/Post3.java)

The commands must be immutable to avoid concurrency issues that may occur from changing a command instance that has been sent.

The section [[Immutable Objects|Immutable]] describes how to define immutable command classes.

## Event Handlers

When an event has been persisted successfully the current state is updated by applying the event to the current state. The functions for updating the state are registered with the `setEventHandler` method of the `BehaviorBuilder`.

@[event-handler](code/docs/home/persistence/Post2.java)

You should define one event handler for each event class that the entity can persist.

The event handler returns the new state. The state must be immutable, so you return a new instance of the state. Current state can be accessed from the event handler with the `state` method of the `PersistentEntity`. The same event handlers are also used when the entity is started up to recover its state from the stored events.

The events must be immutable to avoid concurrency issues that may occur from changing an event instance that is about to be persisted.

The section [[Immutable Objects|Immutable]] describes how to define immutable event classes.

## Replies

Each command must define what type of message to use as reply to the command by implementing the [PersistentEntity.ReplyType](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntity.ReplyType.html) interface.

@[AddPost](code/docs/home/persistence/BlogCommand.java)

You send the reply message using the `reply` method of the context that is passed to the command handler function.

Typically the reply will be an acknowledgment that the entity processed the command successfully, i.e. you send it after persist.

@[reply](code/docs/home/persistence/Post3.java)

For convenience you may use the `akka.Done` as acknowledgment message.

It can also be a reply to a read-only query command.

@[read-only-command-handler](code/docs/home/persistence/Post3.java)

You can use `ctx.invalidCommand` to reject an invalid command, which will fail the `CompletionStage` with `PersistentEntity.InvalidCommandException` on the sender side.

You can send a negative acknowledgment with `ctx.commandFailed`, which will fail the `CompletionStage` on the sender side with the given exception.

If persisting the events fails a negative acknowledgment is automatically sent, which will fail the `CompletionStage` on the sender side with `PersistentEntity.PersistException`.

If the `PersistentEntity` receives a command for which there is no registered command handler a negative acknowledgment is automatically sent, which will fail the `CompletionStage` on the sender side with `PersistentEntity.UnhandledCommandException`.

If you don't reply to a command the `CompletionStage` on the sender side will be completed with a `akka.pattern.AskTimeoutException` after a timeout.

## Changing Behavior

The event handlers are typically only updating the state, but they may also change the behavior of the entity in the sense that new functions for processing commands and events may be defined. This is useful when implementing finite state machine (FSM) like entities. Event handlers that change the behavior are registered with the `setEventHandlerChangingBehavior` of the `BehaviorBuilder`. Such an event handler returns the new `Behavior` instead of just returning the new state.

@[change-behavior](code/docs/home/persistence/Post2.java)

@[change-behavior-become](code/docs/home/persistence/Post2.java)

In the above example we are creating a completely new `Behavior` with `newBehaviorBuilder`. It is also possible to start with current `Behavior` and modify it. You can access current behavior with the `behavior` method of the `PersistentEntity` and then use the `builder` method of the `Behavior`.

## Snapshots

When the entity is started the state is recovered by replaying stored events. To reduce this recovery time the entity may start the recovery from a snapshot of the state and then only replaying the events that were stored after the snapshot.

Such snapshots are automatically saved after a configured number of persisted events. The snapshot if any is passed as a parameter to the `initialBehavior` method and you should use that state as the state of the returned `Behavior`.

One thing to keep in mind is that if you are using event handlers that change the behavior (`setEventHandlerChangingBehavior`) you must also restore corresponding `Behavior` from the snapshot state that is passed as a parameter to the `initialBehavior` method.

@[snapshot](code/docs/home/persistence/Post3.java)

The state must be immutable to avoid concurrency issues that may occur from changing a state instance that is about to be saved as snapshot.

The section [[Immutable Objects|Immutable]] describes how to define immutable state classes.


## Usage from Service Implementation

To access an entity from a service implementation you first need to inject the [PersistentEntityRegistry](api/index.html?com/lightbend/lagom/javadsl/persistence/PersistentEntityRegistry.html) and at startup (in the constructor) register the class that implements the `PersistentEntity`.

In the service method you retrieve a `PersistentEntityRef` for a given entity identifier from the registry. Then you can send the command to the entity using the `ask` method of the `PersistentEntityRef`. `ask` returns a `CompletionStage` with the reply message.

@[service-impl](code/docs/home/persistence/BlogServiceImpl.java)

In this example we are using the command `AddPost` also as the request parameter of the service method, but you can of course use another type for the external API of the service.

The commands are sent as messages to the entity that may be running on a different node. If that node is not available due to network issues, JVM crash or similar the messages may be lost until the problem has been detected and the entities have been migrated to another node. In such situations the `ask` will time out and the `CompletionStage` will be completed with `akka.pattern.AskTimeoutException`.

Note that the `AskTimeoutException` is not a guarantee that the command was not processed. For example, the command might have been processed but the reply message was lost.

## Serialization

JSON is the recommended format the persisted events and state.  The [[Serialization|Serialization]] section describes how to add Jackson serialization support to such classes and also how to evolve the classes, which is especially important for the persistent state and events, since you must be able to deserialize old objects that were stored.

## Unit Testing

For unit testing of the entity you can use the [PersistentEntityTestDriver](api/index.html?com/lightbend/lagom/javadsl/testkit/PersistentEntityTestDriver.html), which will run the `PersistentEntity` without using a database. You can verify that it emits expected events and side-effects in response to incoming commands.

@[unit-test](code/docs/home/persistence/PostTest.java)

`run` may be invoked multiple times to divide the sequence of commands into manageable steps. The [Outcome](api/index.html?com/lightbend/lagom/javadsl/testkit/PersistentEntityTestDriver.Outcome.html) contains the events and side-effects of the last `run`, but the state is not reset between different runs.

Note that it also verifies that all commands, events, replies and state are [[serializable|Serialization]], and reports any such problems in the `issues` of the `Outcome`.

To use this feature add the following in your project's build.

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-testkit_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
    <scope>test</scope>
</dependency>
```

In sbt:

@[testkit-dependency](code/build-cluster.sbt)

## Full Example

@[full-example](code/docs/home/persistence/Post.java)

@[full-example](code/docs/home/persistence/BlogCommand.java)

@[full-example](code/docs/home/persistence/BlogEvent.java)

@[full-example](code/docs/home/persistence/BlogState.java)

## Refactoring Consideration

If you change the class name of a `PersistentEntity` you have to override `entityTypeName` and retain the original name because this name is part of the key of the store data (it is part of the `persistenceId` of the underlying `PersistentActor`). By default the `entityTypeName` is using the short class name of the concrete `PersistentEntity` class.

## Configuration

The default configuration should be good starting point, and the following settings may later be amended to customize the behavior if needed.  The following is a listing of the non database specific settings for Lagom persistence:

@[persistence](../../../../../persistence/core/src/main/resources/reference.conf)

## Underlying Implementation

Each `PersistentEntity` instance is executed by a [PersistentActor](https://doc.akka.io/docs/akka/2.6/persistence.html?language=java) that is managed by [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/cluster-sharding.html?language=java).

## Execution details (advanced)

If you've read all the sections above you are familiar with all the pieces conforming a Persistent Entity but there are few details worth explaining more extensively. As stated above:

> Commands are processed sequentially, one at a time, for a specific entity instance.

This needs a deeper explanation to understand the guarantees provided by Lagom. When a command is received, the following occurs:

1. a command handler is selected, if none is found an `UnhandledCommandException` is thrown
2. the command handler is invoked for the command, one or more events may be emitted (to process a command that emits no events, `setReadOnlyCommandHandler` must be used)
3. events are applied to the appropriate event Handler (this can cause `Behavior` changes so defining the command handler on a behavior doesn't require all event handlers to be supported on that behavior)
4. if applying the events didn't cause any exception, events are persisted atomically and in the same order they were emitted on the command handler
5. if there's an `afterPersist`, then it is invoked (only once)
6. if the snapshotting threshold is exceeded, a snapshot is generated and stored
7. finally, the command processing completes and a new command may be processed.

If you are familiar with [Akka Persistence](https://doc.akka.io/docs/akka/2.6/persistence.html) this process is slightly different in few places:

* new commands are not processed until events are stored, the `Effect` completed and the snapshot updated (if necessary). Akka provides the same behavior and also `async` alternatives that cause new commands to be processed even before all event handlers have completed.
* saving snapshots is an operation run under the covers _at least_ every `lagom.persistence.snapshot-after` events (see [Configuration](#Configuration) above) but "storing events atomically" takes precedence. Imagine we want a snapshot every 100 events and we already have 99 events, if the next command emits 3 events the snapshot will only be stored after event number 102 because events `[100, 101, 102]` will be stored atomically and only after it'll be possible to create a snapshot.

