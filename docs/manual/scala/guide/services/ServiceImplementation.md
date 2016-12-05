# Implementing services

Services are implemented by providing an implementation of the service descriptor trait, implementing each call specified by that descriptor.

For example, here's an implementation of the `HelloService` descriptor:

@[hello-service-impl](code/ServiceImplementation.scala)

As you can see, the `sayHello` method is implemented using the `apply` factory method on [`ServiceCall`](api/com/lightbend/lagom/scaladsl/api/ServiceCall$.html). This takes a function of `Request => Future[Response]` and returns a [`ServiceCall`](api/com/lightbend/lagom/scaladsl/api/ServiceCall.html) whose `invoke` method simply delegates to that function.  An important thing to realise here is that the invocation of `sayHello` itself does not execute the call, it only returns the service call to be executed.  The advantage here is that when it comes to composing the call with other cross cutting concerns, such as authentication, this can easily be done using ordinary function based composition.

Let's have a look at our [`ServiceCall`](api/com/lightbend/lagom/scaladsl/api/ServiceCall.html) interface again:

@[service-call](code/ServiceDescriptors.scala)

It will take the request, and return the response as a [`Future`](http://www.scala-lang.org/api/2.12.x/scala/concurrent/Future.html).  If you've never seen `Future` before, it is a value that may not be available until later.  When an API returns a future, that value might not yet be computed, but the API promises that at some point in future, it will be.  Since the value isn't computed yet, you can't interact with it immediately.  What you can do though is attach callbacks that transform the promise to a promise of a new value, using the `map` and `flatMap` methods.  `Future` with its `map` and `flatMap` methods are fundamental building blocks for doing reactive programming in Scala, they allow your code to be asynchronous, not waiting for things to happen, but attaching callbacks that react to computations being completed.

Of course, a simple hello world computation is not asynchronous, all it needs is to do is build a String, and that returns immediately.  In this case, we need to wrap the result of that in a `Future`.  This can be done by calling `Future.successful()`, which returns a future that has an immediately available value.

## Wiring together a Lagom application

Having provided an implementation of the service, we can now register that with the Lagom framework.  Lagoms Scala API is built on top of Play Framework, and uses Play's [compile time dependency injection support](https://www.playframework.com/documentation/2.5.x/ScalaCompileTimeDependencyInjection) to wire together a Lagom application.

Although it's not strictly necessary, we recommend that you use [Macwire](https://github.com/adamw/macwire) to assist in wiring dependencies together. Macwire provides some very lightweight macros that locate dependencies for the components you wish to create so that you don't have to manually wire them together yourself. Macwire can be added to your service by adding the following to your service implementations dependencies:

@[macwire](code/macwire.sbt)

Next we need to create an application cake. The simplest way of doing this is by creating an abstract class that extends [`LagomApplication`](api/com/lightbend/lagom/scaladsl/server/LagomApplication.html):

@[lagom-application](code/ServiceImplementation.scala)

The important method to implement here is the `lagomServer` method. Lagom will use this to discover your service bindings and create a Play router for handling your service calls. You can see that we've bound one service descriptor, the `HelloService`, to our `HelloServiceImpl` implementation. We've used Macwire's `wire` macro to wire the dependencies - at the moment our service actually has no dependencies so we could just construct it manually ourselves, but it's not likely that a real service implementation would have no dependencies.

You can see that we've also mixed in the Play `AhcWSComponents` trait. Play's HTTP client, the WS API, which is used by Lagom for making service calls, is pluggable, and so an implementation needs to be selected when we wire our application together. We've selected Play's async-http-client implementation, provided by `AhcWSComponents`.

The `HelloApplication` is an abstract class, the reason for this is that there is still one method that hasn't been implemented, the `serviceLocator` method. A typical application will use different service locators in different environments, in development, it will use the service locator provided by the Lagom development environment, while in production it will use whatever is appropriate for your production environment, such as the `ConductR` service locator implementation. So our main application cake leaves this method abstract so that it can mix in the right one depending on which mode it is in when the application gets loaded.

Having created our application cake, we can now write an application loader. Play's mechanism for loading an application is for the application to provide an application loader. Play will pass some context information to this loader, such as a classloader, the running mode, and any extra configuration, so that the application can bootstrap itself. Lagom provides a convenient mechanism for implementing this, the [`LagomApplicationLoader`](api/com/lightbend/lagom/scaladsl/server/LagomApplicationLoader.html):

@[lagom-loader](code/ServiceImplementation.scala)

The loader has two methods, `load` and `loadDevMode`. You can see that we've mixed in different service locators for each method, we've mixed in [`LagomDevModeComponents`](api/com/lightbend/lagom/scaladsl/server/LagomDevModeComponents.html) that provides the dev mode service locator and registers the services with it in dev mode, and in prod mode, for now, we've simply provided [`NoServiceLocator`](api/com/lightbend/lagom/scaladsl/api/ServiceLocator$$NoServiceLocator$.html) as the service locator - this is a service locator that will return nothing for every lookup. We'll see in the [[deploying to production|ProductionOverview]] documentation how to select the right service locator for production.

Finally, we need to tell Play about our application loader. We can do that by adding the following configuration to `application.conf`:

    play.application.loader += com.example.HelloLoader

## Working with streams

When the request and response bodies are strict, working with them is straightforward.  If they are streamed, however, you'll need to use Akka streams to work with them.  Let's take a look at how some of the streamed service calls in the [[service descriptors|ServiceDescriptors#Streamed-messages]] examples might be implemented.

The `tick` service call is going to return a `Source` that sends messages at the specified interval. Akka streams has a helpful constructor for such a stream:

@[tick-service-call](code/ServiceImplementation.scala)

The first two arguments are the delay before messages should be sent, and the interval at which they should be sent. The third argument is the message that should be sent on each tick. Calling this service call with an interval of `1000` and a request message of `tick` will result in a stream being returned that sent a `tick` message every second.

A streamed `sayHello` service call can be implemented by mapping the incoming `Source` of the names to say hello to:

@[hello-service-call](code/ServiceImplementation.scala)

When you `map` a `Source`, you get back a new `Source` that applies the map transformation to each message that the incoming `Source` produces.

These examples of working with streams are obviously quite trivial.  The sections on [[Publish-Subscribe|PubSub]] and [[Persistent Read-Side|ReadSide]] show real examples of using streams in Lagom.

## Handling headers

Sometimes you may need to handle the request header, or add information to the response header.  `ServiceCall` provides `handleRequestHeader` and `handleResponseHeader` methods to allow you to do this, however it is not recommended that you implement these directly, rather, you should use [`ServerServiceCall`](api/com/lightbend/lagom/scaladsl/server/ServerServiceCall.html).

`ServerServiceCall` is an interface that extends `ServiceCall`, and provides an additional method, `invokeWithHeaders`.  This is different from the regular `invoke` method because in addition to the `Request` parameter, it also accepts a [`RequestHeader`](api/com/lightbend/lagom/scaladsl/api/transport/RequestHeader.html) parameter.  And rather than returning a `Future[Response]`, it returns a `Future[(ResponseHeader, Response)]`.  Hence it allows you to handle the request header, and send a custom response header.  `ServerServiceCall` implements the `handleRequestHeader` and `handleResponseHeader` methods, so that when Lagom calls the `invoke` method, it is delegated to the `invokeWithHeaders` method.

The [`ServerServiceCall`](api/com/lightbend/lagom/scaladsl/server/ServerServiceCall$.html) companion object provides a factories for creating `ServerServiceCall`'s both that work with headers and that don't. It may seem counter intuitive to be able to create a `ServerServiceCall` that doesn't work with headers, but the reason for doing this is to assist in service call composition, where a composing service call might want to compose both types of service call.

Here's an example of working with the headers:

@[server-service-call](code/ServiceImplementation.scala)

## Service call composition

You may have situations where you want to compose service calls with cross cutting concerns such as security or logging.  In Lagom, this is done by composing service calls explicitly.  The following shows a simple logging service call:

@[logging-service-call](code/ServiceImplementation.scala)

This uses the `compose` method from `ServerServiceCall`, which takes a callback that takes the request header, and returns a service call.

If we were to implement the `HelloService` to be logged, we would use it like this:

@[logged-hello-service](code/ServiceImplementation.scala)

Another common cross cutting concern is authentication.  Imagine you have a user storage interface:

@[user-storage](code/ServiceImplementation.scala)

You could use it like this to implement an authenticated service call:

@[auth-service-call](code/ServiceImplementation.scala)

This time, since the lookup of the user is asynchronous, we're using `composeAsync`, which allows us to asynchronously return the service call to handle the service.  Also, instead of simply accepting a service call, we accept a function of a user to a service call.  This means the service call can access the user:

@[auth-hello-service](code/ServiceImplementation.scala)

Notice here, in contrast to other frameworks where a user object may be passed using thread locals or in an untyped map by filters, the user object is explicitly passed.  If your code requires access to the user object, it's impossible to have a configuration error where you forgot to put the filter in place, the code simply will not compile.

Often you will want to compose multiple service calls together.  This is where the power of function based composition really shines, in contrast to annotations.  Since service calls are just regular methods, you can simply define a new method that combines them, like so:

@[compose-service-call](code/ServiceImplementation.scala)

Using this in the hello service:

@[filter-hello-service](code/ServiceImplementation.scala)
