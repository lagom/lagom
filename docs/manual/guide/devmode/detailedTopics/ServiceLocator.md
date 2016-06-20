# Service Locator

A Service Locator is embedded in Lagom's development environment, allowing services to discover and communicate with each others. There are a number of settings and tasks available to tune the embedded Service Locator to your liking, let's explore them:

## Default port

By default, the service locator is running on port `8000`, but it is possible to use a different port. For instance, by adding the following in your build, the Service Locator is run on port `10000`:

@[service-locator-port](code/build-service-locator.sbt)

## Communicating with external services

It is possible to enable communication between the Lagom services defined in your build, and an unbounded number of external services (which could either be running locally or on a different machine). The first thing you will have to do is to register each external service in the Service Locator. Assume we want to register an external service named `weather` that is running on `http://localhost:3333`, here is what we would add to the build:

@[service-locator-unmanaged-services](code/build-service-locator.sbt)

The above ensures that the Service Locator knows about the `weather` service. Then, if you need a Lagom service to communicate with it, simply `@Inject` the [`ServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html) and use it to either locate the `weather` service's URI, or perform some arbitrary work with it.

### Integrating with external Lagom projects

Note that if the service you want to communicate with is actually a Lagom service, you may want to read the documentation for [[integrating with an external Lagom projects|MultipleBuilds]].

## Start and stop

The Service Locator is automatically started when executing the `runAll` task. However, there are times when you might want to manually start only a few services, and hence you won't use the `runAll` task. In this case, you can manually start the Service Locator via the `lagomServiceLocatorStart` task, and stopping it with the `lagomServiceLocatorStop` task.

## Disable it

You can disable the embedded Service Locator by adding the following in your build:

@[service-locator-disabled](code/build-service-locator.sbt)

Be aware that by disabling the Service Locator your services will not be able to communicate. To restore communication, you will have to provide an implementation of [`ServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html).
