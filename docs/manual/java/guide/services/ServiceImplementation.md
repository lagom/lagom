# Implementing services

Services are implemented by providing an implementation of the service descriptor interface, implementing each call specified by that descriptor.

For example, here's an implementation of the `HelloService` descriptor:

@[hello-service-impl](code/docs/services/HelloServiceImpl.java)

As you can see, the `sayHello()` method is implemented using a lambda.  An important thing to realise here is that the invocation of `sayHello()` itself does not execute the call, it only returns the call to be executed.  The advantage here is that when it comes to composing the call with other cross cutting concerns, such as authentication, this can easily be done using ordinary function based composition.

If you've used a Java based web framework before, you may be familiar with using annotations for composition of cross cutting concerns.  Annotations have their limits - they don't compose, that is, if you have two different annotations that you want to apply to many different methods, it's not straight forward to simply create a new annotation that combines them.  In contrast, functions are just methods, if you want to compose two methods together, you create a new method that invokes both of them.  Additionally, you're in complete control over how they get composed, you know exactly what order they are composed in, as opposed to annotations where it's up to the framework to magically read them via reflection and somehow gain meaning from that.

Now let's have a look at our [ServiceCall](api/index.html?com/lightbend/lagom/javadsl/api/ServiceCall.html) interface again:

```java
interface ServiceCall<Request, Response> {
  CompletionStage<Response> invoke(Request request);
}
```

It will take the request, and return the response as a [`CompletionStage`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html).  If you've never seen `CompletionStage` before, it is a promise.  When an API returns a promise, that value might not yet be computed, but the API promises that at some point in future, it will be.  Since the value isn't computed yet, you can't interact with it immediately.  What you can do though is attach callbacks that transform the promise to a promise of a new value, using the `thenApply` and `thenCompose` methods.  `CompletionStage` with its `thenApply` and `thenCompose` methods are fundamental building blocks for building reactive applications in Java, they allow your code to be asynchronous, not waiting for things to happen, but attaching callback that react to computations being completed.

Of course, a simple hello world computation is not asynchronous, all it needs is to do is concatenate two Strings, and that returns immediately.  In this case, we need to wrap the result of that in a `CompletionStage`.  This can be done by calling `CompletableFuture.completedFuture()`, which returns a subclass of `CompletionStage` wrapping an immediately available value.

Having provided an implementation of the service, we can now register that with the Lagom framework.  Lagom is built on top of Play Framework, and so uses Play's Guice based [dependency injection support](https://playframework.com/documentation/2.6.x/JavaDependencyInjection) to register components.  To register a service, you'll need to implement a Guice module.  This can be done by creating a class called `Module` in the root package:

@[hello-service-binding](code/docs/services/server/Module.java)

As you can see, the module extends Guice's `AbstractModule`, as well as Lagom's `ServiceGuiceSupport`.  In this module, you can provide any Guice bindings you like.  In this case, we're just providing a binding for the `HelloService`.  `bindService()` may only be invoked once, as this will bind a router for Play to use to route Lagom service calls, which if bound multiple times, will cause a Guice configuration error. The name in the Descriptor bound will be used to name your Service. This name will be used by Lagom as the default value to identify your microservice when interacting with other microservices.

By convention, Play will automatically load a module called `Module` in the root package if it can find one, however if you'd prefer to call your module another name, or not put it in the root package, then you can manually add your module to Play's list of enabled modules by adding the following to `application.conf`:

    play.modules.enabled += com.example.MyModule

## Working with streams

When the request and response bodies are strict, working with them is straightforward.  If they are streamed, however, you'll need to use Akka streams to work with them.  Let's take a look at how some of the streamed service calls in the [[service descriptors|ServiceDescriptors#Streamed-messages]] examples might be implemented.

The `tick` service call is going to return a `Source` that sends messages at the specified interval.  Akka streams has a helpful constructor for such a stream:

@[tick-service-call](code/docs/services/ServiceImplementation.java)

The first two arguments are the delay before messages should be sent, and the interval at which they should be sent.  The third argument is the message that should be sent on each tick.  Calling this service call with an interval of `1000` and a request message of `tick` will result in a stream being returned that sent a `tick` message every second.

The `sayHello` service call can be implemented by mapping the incoming `Source` of the names to say hello to:

@[hello-service-call](code/docs/services/ServiceImplementation.java)

When you `map` a `Source`, you get back a new `Source` that applies the map transformation to each message that the incoming `Source` produces.

These examples of working with streams are obviously quite trivial.  The sections on [[Publish-Subscribe|PubSub]] and [[Persistent Read-Side|ReadSide]] show real examples of using streams in Lagom.

## Handling headers

Sometimes you may need to handle the request header, or add information to the response header.  `ServiceCall` provides `handleRequestHeader` and `handleResponseHeader` methods to allow you to do this, however it is not recommended that you implement this directly, rather, you should use `ServerServiceCall`.

`ServerServiceCall` is an interface that extends `ServiceCall`, and provides an additional method, `invokeWithHeaders`.  This is different from the regular `invoke` method because in addition to the `Request` parameter, it also accepts a `RequestHeader` parameter.  And rather than returning a `CompletionStage<Response>`, it returns a `CompletionStage<Pair<ResponseHeader, Response>>`.  Hence it allows you to handle the request header, and send a custom response header.  `ServerServiceCall` implements the `handleRequestHeader` and `handleResponseHeader` methods, so that when Lagom calls the `invoke` method, it is delegated to the `invokeWithHeaders` method.

`ServerServiceCall` is a functional interface, leaving the original `invoke` method abstract, so when an interface requires you to pass or return a `ServerServiceCall`, if you implement it with a lambda, you aren't forced to handle the headers.  An additional functional interface is provided, `HeaderServiceCall`, this extends `ServerServiceCall` and makes `invokeWithHeaders` the abstract method.  This can be used to handle headers with a lambda implemented service call, in two ways.

If you're implementing the service call directly, you can simply change the return type to be `HeaderServiceCall`, like so:

@[header-service-call-lambda](code/docs/services/ServiceImplementation.java)

If you're required to pass or return a `ServerServiceCall`, you can use the `HeaderServiceCall.of` method, like so:

@[header-service-call-of-lambda](code/docs/services/ServiceImplementation.java)

See [Header Manipulation and HTTP testing](https://github.com/lagom/lagom-recipes/blob/master/http-header-handling/http-header-handling-java-sbt/README.md) for an example of status code manipulation and how to test it.

## Service call composition

You may have situations where you want to compose service calls with cross cutting concerns such as security or logging.  In Lagom, this is done by composing service calls explicitly.  The following shows a simple logging service call:

@[logging-service-call](code/docs/services/ServiceImplementation.java)

This uses the `compose` method from `HeaderServiceCall`, which takes a callback that takes the request header, and returns a service call.

If we were to implement the `HelloService` to be logged, we would use it like this:

@[logged-hello-service](code/docs/services/ServiceImplementation.java)

Another common cross cutting concern is authentication.  Imagine you have a user storage interface:

@[user-storage](code/docs/services/ServiceImplementation.java)

You could use it like this to implement an authenticated service call:

@[auth-service-call](code/docs/services/ServiceImplementation.java)

This time, since the lookup of the user is asynchronous, we're using `composeAsync`, which allows us to asynchronously return the service call to handle the service.  Also, instead of simply accepting a service call, we accept a function of a user to a service call.  This means the service call can access the user:

@[auth-hello-service](code/docs/services/ServiceImplementation.java)

Notice here, in contrast to other frameworks where a user object may be passed using thread locals or in an untyped map by filters, the user object is explicitly passed.  If your code requires access to the user object, it's impossible to have a configuration error where you forgot to put the filter in place, the code simply will not compile.

Often you will want to compose multiple service calls together.  This is where the power of function based composition really shines, in contrast to annotations.  Since service calls are just regular methods, you can simply define a new method that combines them, like so:

@[compose-service-call](code/docs/services/ServiceImplementation.java)

Using this in the hello service:

@[filter-hello-service](code/docs/services/ServiceImplementation.java)
