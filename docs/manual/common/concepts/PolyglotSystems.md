# Polyglot systems with Lagom

Lagom does not expect that every service in your system will be a Lagom microservice.  After all, a big advantage of using microservices in the first place is to pragmatically select the best language and technology for each service. Lagom's idiomatic usage of standard communication protocols and general compatibility with design driven API approaches allow it to work well in such a polyglot system.

Lagom service calls map down to standard HTTP for synchronous communication and WebSockets for streaming and asynchronous messaging. Any language or framework that supports these protocols can easily consume a Lagom service.  Conversely, a Lagom service can easily talk to any service that exposes a REST API.

A Lagom service's API specifies how that service uses HTTP. REST service calls are identified by an HTTP method and URI. Request and response headers can be customized. Lagom messages are serialized, by default, to ordinary JSON, using idiomatic mapping libraries that make it transparent how the JSON will be represented on the wire.


<!--- Lagom uses the same interfaces in Lagom to Lagom service communication. --->


