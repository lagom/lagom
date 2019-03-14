# What's new in Lagom 1.5?

This page highlights the new features of Lagom 1.5. If you want to learn about the changes you need to make when you migrate to Lagom 1.5, check out the [[Lagom 1.5 Migration Guide|Migration15]].



## TLS Support

Lagom in both [[dev mode|ConfiguringServicesInDevelopment#Using-HTTPS-in-development-mode]] and [[tests|Test#How-to-use-TLS-on-tests]] supports basic usage of TLS by means of self-signed certificates provided by the framework.

## Cluster Formation

A new mechanism to form and [[join an Akka Cluster|Cluster#Joining]] is introduced in Lagom 1.5. Apart from the original `Manual Cluster Formation` the new `Akka Cluster Bootstrap` is now supported. This new mechanism is introduced with lower precedence than `Manual Cluster Formation` so if you rely on the use of a list of `seed-nodes` then everything will work as before. On the other hand, `Akka Cluster Bootstrap` takes precedence over the `join-self` cluster formation for single-node clusters. If you use single-node clusters via `join-self` you will have to explicitly disable `Akka Cluster Bootstrap`:

```
lagom.cluster.bootstrap.enabled = false
```

##  Additional Routers

As of Lagom 1.5 it is possible to extend the routes exposed by your service. So your service will not only expose the calls listed in `Service.Descriptor` but will also serve endpoints handled by `additionalRouters`. The [[documentation|AdditionalRouters]] covers all the details. Additional routers make it trivial to extend a `Service.Descriptor` with features natively supported by Play such as uploading a file. There is also a [Lagom recipe](https://github.com/lagom/lagom-recipes/tree/master/file-upload) detailing such a use case.

## gRPC

Lagom 1.5 introduces support for cross-service gRPC communication. [gRPC](https://grpc.io/) is a high-performance, open-source universal RPC framework. Lagom's support for gRPC is built on top of [Play-gRPC](https://developer.lightbend.com/docs/play-grpc/current/) using the new `additionalRouter` feature in Lagom. 

## Deployment

Neither ConductR not [Lightbend Orchestration](https://developer.lightbend.com/docs/lightbend-orchestration/current/) are supported in Lagom 1.5. See the [[Deployment|Migration15#Deployment]] section on the migration guide for more details.
