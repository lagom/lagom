# Service Locator

A Service Locator is embedded in Lagom's development environment, allowing services to discover and communicate with each others. There are a number of settings and tasks available to tune the embedded Service Locator to your liking, let's explore them:

## Default port

By default, the service locator runs on port `9008`, but it is possible to use a different port. For instance, you can tell the service locator to run on port `10000` by adding the following to your build.

@[service-locator-port](code/build-service-locator.sbt)

## Communicating with external services

It is possible to enable communication between the Lagom services defined in your build, and an unbounded number of external services (which could either be running locally or on a different machine). The first thing you will have to do is to register each external service in the Service Locator. Assume we want to register an external service named `weather` that is running on `http://localhost:3333`, here is what we would add to the build.

@[service-locator-unmanaged-services](code/build-service-locator.sbt)

The above ensures that the Service Locator knows about the `weather` service. Then, if you need a Lagom service to communicate with it, simply `@Inject` the [`ServiceLocator`](api/com/lightbend/lagom/scaladsl/api/ServiceLocator.html) and use it to either locate the `weather` service's URI, or perform some arbitrary work with it.

### Integrating with external Lagom projects

Note that if the service you want to communicate with is actually a Lagom service, you may want to read the documentation for [[integrating with an external Lagom projects|MultipleBuilds]].



# Service Gateway

Some clients that want to connect to your services will not have access to your Service Locator. External clients need a stable address to communicate to and here's where the Service Gateway comes in. The Service Gateway will expose and reverse proxy all public endpoints registered by your services. A Service Gateway is embedded in Lagom's development environment, allowing clients from the outside (e.g. a browser) to connect to your Lagom services.

## Default port

By default the Service Gateway is listening for connections on port `9000` in `localhost`. It is possible to change that port by adding this to your build.

In sbt:

@[service-gateway-port](code/build-service-locator.sbt)


## Default gateway implementation

The Lagom development environment provides an implementation of a Service Gateway based on [Akka HTTP](https://github.com/akka/akka-http) and the (now legacy) implementation based on [Netty](https://netty.io/).

You may opt in to use the old `netty` implementation with this setting in sbt:

@[service-gateway-impl](code/build-service-locator.sbt)



## Start and stop

The Service Locator and the Service Gateway are automatically started when executing the `runAll` task. However, there are times when you might want to manually start only a few services, and hence you won't use the `runAll` task. In this case, you can manually start the Service Locator and Service Gateway pair via the `lagomServiceLocatorStart` sbt task, and stopping it with the `lagomServiceLocatorStop` sbt task.

## Disable it

You can disable the embedded Service Locator and Service Gateway by adding the following in your build.

@[service-locator-disabled](code/build-service-locator.sbt)

Be aware that by disabling the Service Locator your services will not be able to communicate with each other. To restore communication, you will have to provide an implementation of [`ServiceLocator`](api/com/lightbend/lagom/scaladsl/api/ServiceLocator.html) in your service. You will also be unable to access to your services via the Service Gateway running on http://localhost:9000 (by default). Instead, you will need to access each service directly on its own port. Each service port is logged to the console when starting in development mode, for example:

```
[info] Service hello-impl listening for HTTP on 0:0:0:0:0:0:0:0:57797
```

For more information, see [[How are ports assigned to services?|ServicePort]].
