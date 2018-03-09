# Consuming services

We've seen how to define service descriptors and how to implement them, now we need to consume them.  The service descriptor contains everything Lagom needs to know about how to invoke a service, consequently, Lagom is able to implement service descriptor interfaces for you.

## Binding a service client

The first thing necessary to consume a service is to create an implementation of it. Lagom provides a macro to do this on the [`ServiceClient`](api/com/lightbend/lagom/scaladsl/client/ServiceClient.html) class, called `implement`. The `ServiceClient` is provided by the [`LagomServiceClientComponents`](api/com/lightbend/lagom/scaladsl/client/LagomServiceClientComponents.html), which is already implemented by `LagomApplication`, so to create a service client from a Lagom application, you just have to do the following:

@[implement-hello-client](code/ServiceClients.scala)

## Using a service client

Having bound the client, you can now use it anywhere in your Lagom application. Typically this will be done by passing it to another component, such as a service implementation, via that components constructor, which will be done automatically for you if you're using Macwire.  Here's an example of consuming one service from another service:

@[hello-consumer](code/ServiceClients.scala)

## Streaming service client configuration

When using a streaming service client, Lagom will use internally a WebSocket client which has a max frame length parameter. This parameter limits the allowed maximum size for the messages flowing through the WebSocket. This can be configured in `application.conf` on the client-side and the default configuration is:

@[web-socket-client-default](../../../../../service/core/client/src/main/resources/reference.conf)

This configuration will affect all streaming services that the service client consumes. It is not possible to provide different configurations when multiple streaming services are consumed.

Note that the same parameter has to be configured on the server-side using [Play server configuration](https://www.playframework.com/documentation/2.6.x/ScalaWebSockets#Configuring-WebSocket-Frame-Length)

## Circuit Breakers

A [circuit breaker](https://martinfowler.com/bliki/CircuitBreaker.html) is used to provide stability and prevent cascading failures in distributed systems. These should be used in conjunction with judicious timeouts at the interfaces between services to prevent the failure of a single service from bringing down other services.

As an example, we have a web application interacting with a third-party web service. Let's say the third-party has oversold their capacity and their database melts down under load. Assume that the database fails in such a way that it takes a very long time to hand back an error to the third-party web service. This in turn makes calls fail after a long period of time. Back to our web application, the users have noticed that their form submissions take much longer seeming to hang. The users do what they know to do which is use the refresh button, adding more requests to their already running requests. This eventually causes the failure of the web application due to resource exhaustion.

Introducing circuit breakers on the web service call would cause the requests to begin to fail-fast, letting the user know that something is wrong and that they need not refresh their request. This also confines the failure behavior to only those users that are using functionality dependent on the third-party, other users are no longer affected as there is no resource exhaustion. Circuit breakers can also allow savvy developers to mark portions of the site that use the functionality unavailable, or perhaps show some cached content as appropriate while the breaker is open.

A circuit breaker has 3 states:

[[circuit-breaker-states.png]]

During normal operation, a circuit breaker is in the **Closed** state:

* Exceptions or calls exceeding the configured `call-timeout` increment a failure counter
* Successes reset the failure count to zero
* When the failure counter reaches a `max-failures` count, the breaker is tripped into Open state

While in **Open** state:

* All calls fail-fast with a `CircuitBreakerOpenException`
* After the configured `reset-timeout`, the circuit breaker enters a Half-Open state

In **Half-Open** state:

* The first call attempted is allowed through without failing fast
* All other calls fail-fast with an exception just as in Open state
* If the first call succeeds, the breaker is reset back to Closed state
* If the first call fails, the breaker is tripped again into the Open state for another full resetTimeout

All service calls with Lagom service clients are by default using circuit breakers. Circuit Breakers are used and configured on the client side, but the granularity and configuration identifiers are defined by the service provider. By default, one circuit breaker instance is used for all calls (methods) to another service. It is possible to set a unique circuit breaker identifier for each method to use a separate circuit breaker instance for each method. It is also possible to group related methods by using the same identifier on several methods.

@[circuit-breaker](code/ServiceClients.scala)

In the above example the default identifier is used for the `sayHi` method, since no specific identifier is given. The default identifier is the same as the service name, i.e. `"hello"` in this example. The `hiAgain` method will use another circuit breaker instance, since `"hello2"` is specified as circuit breaker identifier.

### Circuit Breaker Configuration

On the client side you can configure the circuit breakers. The default configuration is:

@[circuit-breaker-default](../../../../../service/core/client/src/main/resources/reference.conf)

That configuration will be used if you don't define any configuration yourself. The settings to configure a circuit breaker include the general settings you'd expect in a circuit breaker like the number of failures or the request timeout that should open the circuit as well as the timeout that must lapse to close the circuit again. In lagom there's an extra setting to control what is considered a failure.

Lagom's client [[maps all 4xx and 5xx responses to Exceptions|ServiceErrorHandling]] and Lagom's Circuit Breaker defaults to considering all Exceptions as failures. You can change the default behavior by whitelisting particular exceptions so they do not count as failures. Sometimes you want to configure the circuit breaker for a given endpoint so it ignores a certain exception. This is particularly useful when connecting to services where 4xx HTTP status codes are used to model business valid cases. For example, it may be a non-failure case to respond a 404 Not Found to a particular request. In that case you can add `"com.lightbend.lagom.scaladsl.api.transport.NotFound"` to the circuit breaker whitelist so that it is not considered a failure. Even if the `NotFound` exception is not counted as a failure, the client will still throw a `NotFound` exception as a result of invoking the service.

With the above "hello" example we could adjust the configuration by defining properties in `application.conf` such as:

    lagom.circuit-breaker {

      # will be used by sayHi method
      hello.max-failures = 5

      # will be used by hiAgain method
      hello2 {
        max-failures = 7
        reset-timeout = 30s
      }

      # Change the default call-timeout
      # will be used for both sayHi and hiAgain methods
      default.call-timeout = 5s
    }

[Lightbend Monitoring](https://www.lightbend.com/products/monitoring) will provide metrics for Lagom circuit breakers, including aggregated views of the information for all nodes in the cluster.
