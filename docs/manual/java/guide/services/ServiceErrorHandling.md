# Service error handling

Lagom provides a number of different mechanisms for controlling and customising the way errors are handled and reported between services.

There are a number of principles behind the design of Lagom's built in error handling:

* In production, a Lagom service should never give out details of the errors it encounters to another service, unless it knows it is safe to do so.  This is for security reasons, uncensored error messages can be used by attackers to gain detailed information about how a service is implemented.  In practice, this means there are a number of built in exceptions that Lagom considers safe that it will return the details of, and the rest it returns nothing for.
* In development, it's useful to have full error messages sent over the wire.  Lagom will attempt to send useful information about exceptions when the service is running in development.
* If possible, Lagom will try to reconstruct errors on the client side when thrown on the service side.  So, if the server side throws an exception saying it couldn't serialize something, the client code should receive that same exception.
* If possible, exceptions should be mapped to idiomatic protocol response codes, such as HTTP 4xx and 5xx status codes and WebSocket error close codes.


If you are using Lagom to consume a service (either implemented in Lagom or a third-party stack) the client Lagom provides will map responses with status code values in the ranges 4xx and 5xx to exceptions. That has an impact on the [[Circuit Breakers|ServiceClients#Circuit-Breakers]] the client is using to connect to that endpoint. By default Lagom Circuit Breakers will account any exception as a failure but that behavior is [[configurable|ServiceClients#Circuit-Breaker-Configuration]]. So 4xx and 5xx will be mapped to exceptions but you can whitelist what exceptions should not trip the circuit breaker.

## Exception serializers

Lagom provides an [`ExceptionSerializer`](api/index.html?com/lightbend/lagom/javadsl/api/deser/ExceptionSerializer.html) interface that allows exceptions to be serialized into some form, such as JSON, and an error code to be selected.  It also allows an exception to be recreated from an error code and their serialized form.

Exception serializers convert exceptions to [`RawExceptionMessage`](api/index.html?com/lightbend/lagom/javadsl/api/deser/RawExceptionMessage.html).  The raw exception message contains a status code, which will correspond to an HTTP status code or WebSocket close code, a message body, and a protocol descriptor to say what content type the message is - in HTTP, this will translate to a `Content-Type` header in the response.

The default exception serializer provided by Lagom uses Jackson to serialize exceptions to JSON.  This exception serializer implements the guidelines stated above - it will only return details of the exception if it's a child class of [`TransportException`](api/index.html?com/lightbend/lagom/javadsl/api/transport/TransportException.html), unless in development.  There are a few useful built in subclasses of `TransportException` that you may use, these include [`NotFound`](api/index.html?com/lightbend/lagom/javadsl/api/transport/NotFound.html) and [`PolicyViolation`](api/index.html?com/lightbend/lagom/javadsl/api/transport/PolicyViolation.html).  Lagom will generally be able to throw these exceptions through to the client.  You may also instantiate `TransportException` directly and use that, or you may define a sub class of `TransportException`, however note that Lagom won't throw the subclass in a client since it will not know about it.
