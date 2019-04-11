# What's new in Lagom 1.5?

This page highlights the new features of Lagom 1.5. If you want to learn about the changes you need to make when you migrate to Lagom 1.5, check out the [[Lagom 1.5 Migration Guide|Migration15]].

## Akka Management

Akka Management is a suite of tools for operating Akka powered applications. Akka Management uses a dedicated HTTP port to expose a few routes allowing remote inspection of the state of the Akka Actor System.

Lagom exposes the Akka Management HTTP port out of the box. Lagom will add Health Check routes by default. You can reuse library provided health checks or plugin your own. For example, Lagom uses cluster status to determine when the node is healthy. This is called Cluster Membership Check and is provided by the Akka Cluster HTTP Management library.

## Cluster Formation

A new mechanism to form and [[join an Akka Cluster|Cluster#Joining]] is introduced in Lagom 1.5. Apart from the original `Manual Cluster Formation` the new `Akka Cluster Bootstrap` is now supported. This new mechanism is introduced with lower precedence than `Manual Cluster Formation` so if you rely on the use of a list of `seed-nodes` then everything will work as before. On the other hand, `Akka Cluster Bootstrap` takes precedence over the `join-self` cluster formation for single-node clusters. If you use single-node clusters via `join-self` you will have to explicitly disable `Akka Cluster Bootstrap`:

```
lagom.cluster.bootstrap.enabled = false
```

## gRPC

Lagom 1.5 introduces support for cross-service gRPC communication. [gRPC](https://grpc.io/) is a high-performance, open-source universal RPC framework. The original, HTTP/JSON-based transport is not disappearing but, instead, Lagom introduces gRPC so users can choose to expose alternative transports increasing the adoption of their services. Lagom's support for gRPC is built on top of [Play-gRPC](https://developer.lightbend.com/docs/play-grpc/current/) using the new `additionalRouter` feature in Lagom (see below). 

gRPC must run on HTTP/2. Lagom already supported HTTP/2 since it is built on top of Play. In Lagom 1.5 we’ve reviewed all the necessary pieces so HTTP/2 can also be used on dev mode. In the same spirit, it is now also possible to use encrypted (TLS) communication on dev mode. 

## TLS Support

Lagom in both [[dev mode|ConfiguringServicesInDevelopment#Using-HTTPS-in-development-mode]] and [[tests|Test#How-to-use-TLS-on-tests]] supports basic usage of TLS by means of self-signed certificates provided by the framework.

##  Additional Routers

As of Lagom 1.5 it is possible to extend the routes exposed by your service. So your service will not only expose the calls listed in `Service.Descriptor` but will also serve endpoints handled by `additionalRouters`. The [[documentation|AdditionalRouters]] covers all the details. Additional routers make it trivial to extend a `Service.Descriptor` with features natively supported by Play such as uploading a file. There is also a [Lagom recipe](https://github.com/lagom/lagom-recipes/tree/master/file-upload) detailing such a use case.

## Deployment

Neither ConductR not [Lightbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) are supported in Lagom 1.5. See the [[Deployment|Migration15#Deployment]] section on the migration guide for more details.

## Initial support for Java 11

Lagom 1.5 introduces [Incubating][] support for Java 11, starting with a [change][lagom/lagom#1803] in the use
of Java reflection in Lagom's Java DSL that removes a known obstacle for running on Java 11.  Running Lagom on
Java 11 will be limited to the use of external services that either fully support Java 11 too or run as a
separate process to the Lagom app's Java 11 VM.  For instance, Lagom dev mode may not be able to run an embedded
Cassandra node, requiring it instead be run as a separate process.

[Incubating]: https://developer.lightbend.com/docs/lightbend-platform/2.0/support-terminology/index.html#incubating
[lagom/lagom#1803]: https://github.com/lagom/lagom/pull/1803
