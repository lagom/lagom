# Projections

In Lagom, projections are processes consuming from a Persistent Entity Journal and handling each event via a [[read side table|ReadSide]] or emitting it into a broker topic via a [[`TopicProducer`|MessageBrokerApi#Implementing-a-topic]]. Here, Projections only refer to `ReadSideProcessors` and `TopicProducer`'s' (not Broker subscribers).

Lagom takes care to handle as many instances of your projection as [[shards|ReadSide#Event-tags]] on your journal and then distribute those instances around the cluster so the load is balanced. By default, the multiple instances of your projection will be started but you can opt-out from that behavior using the setting:

```
lagom.projection {
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

The _requested status_ is a volatile, in-memory value but it is replicated across your cluster. Only when the whole cluster is restarted you may have to request a particular status again. Also, because of its replicated nature, the _requested status_ may be overwritten by multiple nodes on your cluster at the same time. The implementation is such that the [last writer wins](https://doc.akka.io/docs/akka/current/distributed-data.html#data-types).

When a new instance of a projection worker is created the rules to decide its status are:

* if worker's requested status is known, use it; if not
* use the default value in `application.conf`

Because the _requested status_ is a distributed, in-memory value, there is an edge case you will have to consider when you need a worker to be stopped. When starting a new node and having that node join an existing cluster, it is possible that some workers are spawned in the node before the _requested status_ value is received from the peer nodes. In that case, the default `lagom.projection.auto-start.enabled` will be used to decide if the spawned worker should be stopped or started. If your default is `enabled = true` but the in-memory, replicated value is `Stopped` then there's a race condition and you could observe your worker _start-and-then-stop_. To prevent the  _start-and-then-stop_ behavior, opt-out of `lagom.projection.auto-start.enabled = true` and always handle worker startup using the methods `startAllWorkers`/`startWorker` on the `Projections` API.
