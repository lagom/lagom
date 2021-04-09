# Splitting a system into multiple builds

For a small system maintained by a single team, it's fine to have all your services in one build. Doing it that way makes it really easy to run all your services with the `runAll` task, as we'll see later in the [[Running Services|RunningServices]] section of this manual.

If you have multiple teams, though, then as described already in [[Lagom build concepts|BuildConcepts]], we recommend splitting your system into multiple builds.

If you aren't concerned with scaling to multiple teams yet, feel free to skip this section for now.

## Publishing services

Even with multiple builds, you will still often want to run your services together in development.  Lagom allows importing services published from one build into another build.

Suppose you have a `hello` service that you want to publish and import into another build.  You can publish this to your local repository by running `publishLocal` from sbt.  This is the simplest way to publish a service, however it means every developer that wants to run a build that imports the service will need publish it to their own repository themselves, and they'll need to do that for each version that they want to import.

More commonly, many developers can share a single Maven or Ivy repository that they can publish and pull artifacts from.  There are a few options for how to do this. If you want to run the repository locally, [Artifactory](https://www.jfrog.com/open-source/) or [Nexus](https://www.sonatype.com/products-overview) are common solutions.  For information on how to configure these in sbt, see [how to publish artifacts](https://www.scala-sbt.org/1.x/docs/Publishing.html) .

## Importing a service

The `hello` Lagom service can be imported by adding the following declaration to your build:

@[hello-external](code/multiple-builds.sbt)

The first argument passed to `lagomExternalScaladslProject` is the name that will be used in your build to refer to this externally defined project. While, the second argument provides the dependency to the `hello-impl` JAR, using the conventional sbt syntax for declaring dependencies. Note in fact that the `lagomExternalScaladslProject` method returns a sbt `Project`, which you can further customize if needed.

You can further configure the service (what ports it is available on, the address it is bound to, etc...) using [[the same settings as a managed Lagom Service|ConfiguringServicesInDevelopment]].

## Mocking a service

Sometimes a service that you import will depend on other services in turn. This can lead to a cascade of importing one service after another, until you find yourself having to run the entire system in your build. Other than being inconvenient to manage, for large systems this may not be feasible. Instead, you might choose to mock some services, providing a fake implementation of a real service's API. The mock implementation is only used to run _other_ services in development that consume that service's API, without having to import the real implementation into their builds. This is similar to the use of mocking libraries in unit tests---such as [Mockito](https://site.mockito.org/), [jMock](http://jmock.org/), or [ScalaMock](https://scalamock.org/)---but at the service level instead of the class level.

Writing and using a mock service implementation is just like writing and using a real service implementation, but with the business logic replaced by simple hard-coded responses. A mock service implementation should not depend on any other services, databases, message brokers, or any other infrastructure. For example, you might be developing an OAuth service that validates user credentials using a separate user authentication service, and then generates and stores API tokens for authenticated users. The user authentication service in turn might interact with a service that handles new user registration, a user profile management service that handles password changes, and an LDAP server for SSO integration. When testing the OAuth service, it isn't always necessary to include all of that additional complexity and test it end-to-end. Instead, you can run a mock user authentication service that implements the same API as the real one, but only supports a small set of test users. Running the mock service in the build for the OAuth service allows you to test the OAuth functionality in isolation from those unrelated concerns.

You can choose to implement the mock service in the same build as the consuming service, by creating a new service implementation in that project that depends on the API library published by the original service. Alternatively, you could implement the mock service in the same build as the real service, publish it alongside the real implementation, and import the mock into the builds of consuming services, as described above.

The benefit of locating the mock in the consuming service build is that it makes it very easy to change the mock implementation as the consuming service is developed, with all of the benefits of Lagom's automatic reloading, and no need to publish the mock to an artifact repository for each change. If the real service has multiple consumers, it allows each consumer to have a custom mock implementation, tailored to its own use of the service.

On the other hand, the benefit of locating the mock next to the real implementation is that a single mock implementation can be shared by multiple consuming service projects. Sometimes, this will be more convenient than creating a mock implementation for each consuming service. The choice is yours.

## Using the external service

Now that you have integrated the `hello` service in your build (or a mock equivalent), any of your Lagom projects can communicate with it after adding a library dependency to its `hello-api` artifact:

@[hello-communication](code/multiple-builds.sbt)

After having added the API dependency to your build for each consumer of the service, we need bind the service client. Lagom uses this binding to provide an implementation of the service's API that uses a client to communicate with the remote service. This can be done using `serviceClient.implement[T]` as explained in [[Binding a service client|ServiceClients#Binding-a-service-client]] .

After providing the binding, just type `reload` in the sbt console. Then, when executing `runAll`, you should see that the `hello` service is started, together with all other services defined in the build:


```console
> runAll
[info] ...
[info] Service hello listening for HTTP on 0:0:0:0:0:0:0:0:22407
[info] ...
(Services started, use Ctrl+D to stop and go back to the console...)
```

## Decoupling with a message broker

Services that use Lagom's [[Message Broker|MessageBroker]] support can entirely avoid the need to run other services in their builds. These services don't need to connect to each other directly, only to Kafka. For development purposes, you can use the `kafka-console-producer` and `kafka-console-consumer` command-line scripts (or other simple Kafka clients) to simulate interactions with other services.
