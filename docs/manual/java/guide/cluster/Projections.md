# Projections

In Lagom, projections are a processes consuming from a Persistent Entity Journal and handling each event via a [[read side table|ReadSide]] or emiting it into a broker topic via a [[`TopicProducer`|MessageBrokerApi#Implementing-a-topic]]. Here, Projections only refer to `ReadSideProcessors` and `TopicProducer`'s' (not Broker subscribers).

Projections are a distributed process. Lagom takes care to handle as many instances of your projection as [[shards|ReadSide#Event-tags]] on your journal and then distribute those instances around the cluster so the load is balanced. By default the multiple instances of your projection will be started but you can opt out from that behavior using the setting:

```
lagom.projection {
  # when enabled, read-side processors and topic producers start at bootstrap
  # when disabled, read-side processors and topic producers are added to projection registry but won't start
  # until a user explicitly sends a request to start it.
  # TODO: add link to Projection API
  auto-start.enabled = true
}
```

## Projection status

Once up and running, you may query the state of your projections to ensure all the instances are up and running. You can also request a particular projection to stop or even a particular instance (aka worker) in a projection to stop. 

Stopping and starting all the workers or a single worker of a projection is an asynchronous operation. Imagine we had a `ReadSideProcessor` called  `users-by-name` that is reading the journal of a `Users` persistent entity and storing data into a SQL table. To stop that projection we would request the status of all workers to become `Stopped`. This request will propagate across the cluster and each node will make sure that any worker running locally that is part of the `users-by-name` projections is stopped. As workers switch from `Started` to `Stopped` they report back and we can `observe` their new status.

To request and modify the status of your projections inject a `Projections` instance in, for example, your `XyzServiceImpl` and use the provided methods.

```java
    @Inject
    public UsersServiceImpl(Projections projections) {
      projections.stopAllWorkers(UsersByNameProcessor.NAME);
    }
```

The _requested status_ is a volatile, in-memory value but it is replicated across your cluster. Only when the whole cluster is restarted you may have to request a particular status again. Also, because of the replicated nature, the _requested status_ may be overwriten by multiple nodes on your cluster at the same time. The implementation is such that [last writer wins](https://doc.akka.io/docs/akka/current/distributed-data.html#data-types).

When a new instance of a projection worker is created the rules to decide its status are:

* if there's worker requested status, use it; if not
* use the default value in `application.conf`
