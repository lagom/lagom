# Integrating with non Lagom services

## Invoking Lagom services

Lagom service calls are implemented using idiomatic REST.  The simplest way to invoke a Lagom service from another framework is to use that frameworks REST client to invoke the Lagom service.

Another way to invoke Lagom services, if the client is running in a JVM, is to use the Lagom service interface directly.

### Using the Lagom service client

#### Configuring dependencies

To use the Lagom service interface, you will need to add a dependency on the Lagom client to your build.  If using sbt, this can be done by adding the following dependency:

@[client-dependency](code/integrating-non-lagom.sbt)

Of course, you will also need to add a dependency to the API project that you have created in your Lagom project.  For more details, see [[Understanding your project structure|LagomBuild#Understanding-your-project-structure]].

#### Creating a client application

Just as in a Lagom service, you create a `LagomApplication`, when creating a Lagom service client in a non Lagom application, you will create a [`LagomClientApplication`](api/com/lightbend/lagom/scaladsl/client/LagomClientApplication.html). This provides and manages the lifecycle of all components necessary to instantiate and use a Lagom service client.

There is one component that you'll need to provide when creating a client application, that is a service locator. It is up to you what service locator you use, it could be a ConductR service locator, a third party service locator, or a service locator created from static configuration.

Lagom provides a number of built-in service locators, including a [`StaticServiceLocator`](api/com/lightbend/lagom/scaladsl/client/StaticServiceLocator.html), a [`RoundRobinServiceLocator`](api/com/lightbend/lagom/scaladsl/client/RoundRobinServiceLocator.html) and a [`ConfigurationServiceLocator`](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocator.html). The easiest way to use these is to mix in their respective `Components` traits. For example, here's a client application built using the static service locator, which uses a static URI:

@[static-service-locator](code/IntegratingNonLagom.scala)

The constructor to `LagomClientApplication` takes two arguments, the first is the name of the client, this name is used for the client to identify itself to these services.  The second argument is a `ClassLoader`.

When you have finished with the application, for example, when the system shuts down, you need to stop the application, by invoking the `stop()` method:

@[stop-application](code/IntegratingNonLagom.scala)

Typically this application will be a singleton in your system.  If your system is using Spring for example, you would create a `FactoryBean` that instantiates it, and you would implement a `@PreDestroy` annotated method that stopped the client application.

#### Creating a client

Once you have created the application, you can easily create a client using it, for example:

@[create-client](code/IntegratingNonLagom.scala)

Here we've created a client for the `HelloService` the same way we would in a regular Lagom application, using `serviceClient.implementClient`.

#### Working with dev mode

When running your service in development, you can tell the service to use Lagom's dev mode service locator, by adding a dependency on Lagom's dev mode support:

@[dev-mode-dependency](code/integrating-non-lagom.sbt)

Then, when you instantiate your application, rather than mixing in your production service locator, you can mix in the [`LagomDevModeServiceLocatorComponents`](api/com/lightbend/lagom/scaladsl/devmode/LagomDevModeServiceLocatorComponents.html) trait to get the dev mode service locator:

@[dev-mode](code/IntegratingNonLagom.scala)

You'll also need to configure your application to tell it where the service locator is running, this can be done by passing a system property to your application when it starts up, for example:

```
-Dlagom.service-locator-url=http://localhost:9008
```

Alternatively, you can configure it programmatically by overriding the `devModeServiceLocatorUrl` value on the `LagomDevModeServiceLocatorComponents` trait:

@[dev-mode-url](code/IntegratingNonLagom.scala)
