# Cluster

Instances of the same service may run on multiple nodes, for scalability and redundancy. Nodes may be physical or virtual machines, grouped in a cluster.

The underlying clustering technology is [Akka Cluster](https://doc.akka.io/docs/akka/2.6/cluster-usage.html?language=java).

If instances of a service need to know about each other, they must join the same cluster. Within a cluster, services may use the [[Persistence|PersistentEntity]] and [[Publish-Subscribe|PubSub]] modules of Lagom.

## Dependency

The clustering feature is already included if you are using either of the [[persistence|PersistentEntity]] or [[pubsub|PubSub#Dependency]] modules.

If you want to enable it without those modules, add the following dependency your project's build.

In Maven:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-cluster_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

In sbt:

@[cluster-dependency](code/build-cluster.sbt)

## Cluster composition

A cluster should only span nodes that are running the same service.

You could imagine using cluster features across different services, but we recommend against that, because it would couple the services too tightly. Different services should only interact with each other through each service's API.

## Joining

A service instance joins a cluster when the service starts up.

1. **Joining during development**:  In development you are typically only running the service on one cluster node. No explicit joining is necessary; the [[Lagom Development Environment|DevEnvironment]] handles it automatically and creates a single-node cluster.

1. **Joining during production**: There are several mechanisms available to create a cluster in production. This documentation covers two approaches supported out-of-the-box:
    1. Akka Cluster Bootstrap (recommended)
    2. Manual Cluster Formation (a.k.a. a static list of `seed-nodes`)

The sections below cover the two options for Cluster Joining during Production in more detail.

### Joining during production (Akka Cluster Bootstrap)

Starting from version 1.5.0, Lagom offers support for [Akka Cluster Bootstrap](https://doc.akka.io/docs/akka-management/1.0/bootstrap/). Akka Cluster Bootstrap is enabled by default in production mode and disabled in development and test mode.

Akka Cluster Bootstrap helps forming (or joining to) a cluster by using [Akka Discovery](https://doc.akka.io/docs/akka/2.6/discovery/index.html) to discover peer nodes. It is an alternative to configuring static seed-nodes in dynamic deployment environments such as on Kubernetes or AWS.

It builds on the flexibility of Akka Discovery, leveraging a range of discovery mechanisms depending on the environment you want to run your cluster in.

Akka Cluster Bootstrap, in Lagom, can be disabled through the property `lagom.cluster.bootstrap.enabled = false`. Note that this configuration flag has no effect if you declare seed-nodes explicitly in which case Akka Cluster Bootstrap won't be used.

#### Akka Discovery

In order to find the peer nodes and form a cluster, Akka Cluster Bootstrap need to be configured to use one of the existing Akka Discovery implementations.

The snippet below exemplifies how to configure the Akka Cluster Boostrap to use the Akka Discovery Kubernetes API.

```
akka.management.cluster.bootstrap {
  # example using kubernetes-api
  contact-point-discovery {
    discovery-method = kubernetes-api
    service-name = "hello-lagom"
  }
}
```
[Other existing implementations](https://doc.akka.io/docs/akka-management/1.0/discovery/index.html) are DNS, AWS, Consul, Marathon API, and Static Configuration. It's also possible to provide your own Akka Discovery implementation if needed.

For more detailed and advanced configurations options, please consult the [Akka Cluster Bootstrap](https://doc.akka.io/docs/akka-management/1.0/bootstrap/) documentation and its [reference.conf](https://github.com/akka/akka-management/blob/v1.0.0-RC2/cluster-bootstrap/src/main/resources/reference.conf) file.


#### Akka Management

[Akka Cluster Bootstrap](https://doc.akka.io/docs/akka-management/1.0/bootstrap/) relies on [Akka Management](https://doc.akka.io/docs/akka-management/1.0/akka-management.html) to form a cluster.

[Akka Management](https://doc.akka.io/docs/akka-management/1.0/akka-management.html) is an extension that opens a dedicated HTTP interface. This management extension allows dedicated plugins to include their routes. Akka Cluster Bootstrap uses this mechanism to expose a route. Akka Management will be enabled when the cluster joining mechanism is Cluster Http Management and it will run on http port `8558`. You can configure it to another port by setting property `akka.management.http.port` in your `application.conf` file.

#### Health Checks

Akka Management supports two kinds of [health checks](https://doc.akka.io/docs/akka-management/1.0/healthchecks.html):

  * Readiness checks: should the application receive external traffic, for example waiting for the cluster to form.
  * Liveness checks: should the application be left running

Readiness checks can be used to decide if a load balancer should route traffic where as liveness checks can be used in environments which can restart a hung process.

By default, Lagom enables the Cluster Health Check. This health check includes a readiness check that returns `true` when the node is either `Up` or `WeaklyUp`.

All readiness checks are hosted on `/ready` and liveness checks are hosted on `/alive` on the Akka Management endpoint (port 8558 by default). You can change the paths by configuring it your `application.conf` file:

```
akka.management.health-checks {
  readiness-path = "health/ready"
  liveness-path = "health/alive"
}
```
For further information on Akka Cluster Bootstrap and Health Checks, consult Akka Managment documentation:
 * [Akka Cluster Bootstrap](https://doc.akka.io/docs/akka-management/1.0/bootstrap/)
 * [Http Cluster Management](https://doc.akka.io/docs/akka-management/1.0/cluster-http-management.html)
 * [Health Checks](https://doc.akka.io/docs/akka-management/1.0/healthchecks.html)

### Joining during production (Manual Cluster Formation)

If you prefer to not use **Akka Cluster Bootstrap** and handle the cluster formation yourself, you can configure the Akka Cluster seed nodes statically.

You can define some initial contact points of the cluster, so-called seed nodes in your `application.conf`:

```
akka.cluster.seed-nodes = [
  "akka://MyService@host1:25520",
  "akka://MyService@host2:25520"]
```

Alternatively, this can be defined as Java system properties when starting the JVM:

```
-Dlagom.cluster.bootstrap.enabled=false
-Dakka.cluster.seed-nodes.0=akka://MyService@host1:25520
-Dakka.cluster.seed-nodes.1=akka://MyService@host2:25520
```

The node that is configured first in the list of `seed-nodes` is special. Only that node that will join itself. It is used for bootstrapping the cluster.

The reason for the special first seed node is to avoid forming separated islands when starting from an empty cluster. If the first seed node is restarted and there is an existing cluster it will try to join the other seed nodes, i.e. it will join the existing cluster.

You can read more about cluster joining in the [Akka documentation](https://doc.akka.io/docs/akka/2.6/cluster-usage.html?language=java#joining-to-seed-nodes).

## Downing

When operating a Lagom service cluster you must consider how to handle network partitions (a.k.a. split brain scenarios) and machine crashes (including JVM and hardware failures). This is crucial for correct behavior when using [[Persistent Entities|PersistentEntity]]. Persistent entities must be single-writers, i.e. there must only be one active entity with a given entity identity. If the cluster is split in two halves and the wrong downing strategy is used there will be active entities with the same identifiers in both clusters, writing to the same database. That will result in corrupt data.

The naïve approach is to remove an unreachable node from the cluster membership after a timeout. This works great for crashes and short transient network partitions, but not for long network partitions. Both sides of the network partition will see the other side as unreachable and after a while remove it from its cluster membership. Since this happens on both sides the result is that two separate disconnected clusters have been created. This approach is provided by the opt-in (off by default) auto-down feature in the OSS version of Akka Cluster. Because of this auto-down should not be used in production systems.

**We strongly recommend against using the auto-down feature of Akka Cluster.**

A pre-packaged solution for the downing problem is provided by [Split Brain Resolver](https://doc.akka.io/docs/akka-enhancements/1.1/split-brain-resolver.html), which is part of the [Lightbend Platform](https://www.lightbend.com/lightbend-platform). The `keep-majority` strategy is configured to be enabled by default if you use Lagom with the Split Brain Resolver.

See [[Using Lightbend Platform with Lagom|LightbendPlatform]] and the [Split Brain Resolver documentation](https://doc.akka.io/docs/akka-enhancements/1.1/split-brain-resolver.html) for instructions on how to enable it in the build of your project.

Even if you don't use the commercial Lightbend Platform, you should still read & understand the concepts behind [Split Brain Resolver](https://doc.akka.io/docs/akka-enhancements/1.1/split-brain-resolver.html) to ensure that your solution handles the concerns described there.
