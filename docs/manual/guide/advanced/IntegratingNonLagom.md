# Integrating with non Lagom services

## Invoking Lagom services

Lagom service calls are implemented using idiomatic REST.  The simplest way to invoke a Lagom service from another framework is to use that frameworks REST client to invoke the Lagom service.

Another way to implement Lagom services, if the client is running in a JVM, is to use the Lagom service interface directly.

### Using the Lagom service client

#### Configuring dependencies

To use the Lagom service interface, you will need to add a dependency on the Lagom integration client to your build.  If using maven, this can be done by adding the following dependency:

```xml
    <dependency>
        <groupId>com.lightbend.lagom</groupId>
        <artifactId>lagom-javadsl-integration-client_2.11</artifactId>
        <version>${lagom.version}</version>
    </dependency>
```

Of course, you will also need to add a dependency to the API project that you have created in your Lagom project.  For more details, see [[Understanding your project structure|LagomBuild#Understanding-your-project-structure]].

#### Managing the client factory

The Lagom integration client provides [`LagomClientFactory`](api/index.html?com/lightbend/lagom/javadsl/client/integration/LagomClientFactory.html) creating Lagom client services.  This factory creates and manages thread pools and connection pools, so it's important to manage its lifecycle correctly in your application, ensuring that you only create one instance of it, and to shut it down when you're finished with it.

The factory can be instantiated by invoking the static [`create`](api/index.html?com/lightbend/lagom/javadsl/client/integration/LagomClientFactory.html#create-java.lang.String-java.lang.ClassLoader-) method, for example:

@[create-factory](code/docs/advanced/IntegratingNonLagom.java)

The first argument is a service name, this will be the name of the service that is consuming the Lagom service, and will impact how calls made through this client will identify themselves to the service.  The second argument is a `ClassLoader`, it will be used to create the service proxy and needs to have the API for the client in it.

When you have finished with the factory, for example, when the system shuts down, you need to close the factory, by invoking the [`close`](api/index.html?com/lightbend/lagom/javadsl/client/integration/LagomClientFactory.html#close--) method:

@[close-factory](code/docs/advanced/IntegratingNonLagom.java)

Typically the factory will be a singleton in your system.  If your system is using Spring for example, you would create a `FactoryBean` that instantiates it, and you would implement a `@PreDestroy` annotated method that closed the client factory.

#### Creating a client

Once you have created a client factory, you can easily create a client using it, for example:

@[create-client](code/docs/advanced/IntegratingNonLagom.java)

Here we've created a client for the `HelloService` using the [`createClient`](api/index.html?com/lightbend/lagom/javadsl/client/integration/LagomClientFactory.html#createClient-java.lang.Class-java.net.URI-) method.  We've passed in static URI to tell the client where the `HelloService` lives, typically you would read this from a configuration file on your service.

You can also pass a list of URIs using [`createClient`](api/index.html?com/lightbend/lagom/javadsl/client/integration/LagomClientFactory.html#createClient-java.lang.Class-java.util.Collection-), and finally, if your environment is capable of looking up service URIs dynamically, you can pass an implementation of [`ServiceLocator`](api/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html).

#### Working with dev mode

When running your service in development, you can tell the service to use Lagom's dev mode service locator, using  [`createDevClient`](api/index.html?com/lightbend/lagom/javadsl/client/integration/LagomClientFactory.html#createDevClient-java.lang.Class-).  Typically, you would want to have some configuration in your application that tells you whether it is running in development or not, and only create the dev mode client if you are in development.  For example:

@[dev-mode](code/docs/advanced/IntegratingNonLagom.java)

This means that you don't have to worry about what URI your services are running on in development, you just need to ensure the Lagom `runAll` command has been run to run the service locator.