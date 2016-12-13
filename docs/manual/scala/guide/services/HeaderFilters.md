# Header Filters

In Lagom you may add `HeaderFilter`s to your service descriptor. In a `HeaderFilter` you will usually handle protocol negotiation or authentication. 

A single `HeaderFilter` implementation may transform a request leaving a client or entering a server and a response leaving a server and entering a client. Here's an example that uses the `User-Agent` header to read the service name:

@[user-agent-header-filter](../../../../../service/scaladsl/api/src/main/scala/com/lightbend/lagom/scaladsl/api/transport/HeaderFilter.scala)

This [`UserAgentHeaderFilter`](api/com/lightbend/lagom/scaladsl/api/transport/UserAgentHeaderFilter$.html) is the default `HeaderFilter` any Lagom service will use if none is specified. It uses a `ServicePrincipal` which identifies the client with the service name.

In `UserAgentHeaderFilter` the code at `transformClientRequest` will be invoked when preparing a client invocation to add a `User-Agent` header if a [`ServicePrincipal`](api/com/lightbend/lagom/scaladsl/api/security/ServicePrincipal.html) was specified on the request. Note that by default, Lagom will automatically pass the current services name as the `ServicePrincipal` when it makes a request. On the server end `transformServerRequest` will be used to read the `User-Agent` header and set that value as the request's [Principal](https://docs.oracle.com/javase/8/docs/api/java/security/Principal.html).

Keep in mind that a header filter should only be used to deal with cross cutting _protocol_ concerns, and nothing more. For example, you may have a header filter that describes how the current authenticated user is communicated over the HTTP protocol (by adding a user header, for example). Cross cutting _domain_ concerns, such as authentication and validation, should not be handled in a header filter, rather they should be handled using [[service call composition| ServiceImplementation#Service-call-composition]].

## Header Filter Composition

Each service `Descriptor` can only have one `HeaderFilter`. In order to use several filters at once you may compose them using `HeaderFilter.composite` which will return a `HeaderFilter` that chains all the `HeaderFilter`s you composed. When composing, the order is important so when sending message headers the filters of the composite are used in the order they were provided, and when receiving message headers the filters will be used in reverse order. So if we had the following filter:

@[verbose-filter](code/HeaderFilters.scala)

And we registered two of them with the names `Foo` and `Bar`:

@[header-filter-composition](code/HeaderFilters.scala)

and then called the service, then we would get the following on the server output logs:

```
[debug] Bar - transforming Server Request
[debug] Foo - transforming Server Request
[debug] Foo - transforming Server Response
[debug] Bar - transforming Server Response
```
