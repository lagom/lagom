# Polyglot systems with Lagom

If you use Lagom, do you have to write all of your services in Lagom? The answer is no.

It's true that Lagom does offer an especially seamless experience when multiple Lagom services are talking to each other. In this scenario, service location, communication protocols, and other issues are handled by Lagom transparently. This maximizes developer convenience and productivity.

However, in a microservice based system, not every service is expected to be a Lagom service.  After all, a big advantage of using microservices in the first place is to allow the construction of polyglot systems, selecting the right language and the right technology on a service by service basis.

So while Lagom does provide a great experience when all the services are Lagom services, we do not want to imply that all services should be Lagom services. (In fact, if a large system was implemented using only Lagom services, then perhaps the technical merits of that system should be questioned...?)

Consequently, Lagom is designed to work well in a polyglot environment. Central to ensuring this is the idiomatic usage of standard protocols.

## Lagom communication protocols

Lagom service calls map down to ordinary, standard HTTP and WebSocket requests: plain HTTP for synchronous communication, WebSockets for streaming and asynchronous messaging.

A service's API specifies how that service uses HTTP. REST service calls are identified by an HTTP method and URI. Request and response headers can be customized.

Lagom messages are serialised, by default, to ordinary JSON, using idiomatic mapping libraries that make it transparent how the JSON will be represented on the wire.

The use of ordinary HTTP, WebSockets, and JSON means that any language or framework that supports these protocols can easily consume a Lagom service.  Conversely, a Lagom service can easily talk to any service that offers a REST API.

## Design driven APIs

Lagom's interface specifications are designed to be compatible with design driven API approaches to system development.

### Future plans

Currently Lagom does not include any specific support for design driven API development, aside from using the same interfaces in Lagom to Lagom service communication.

It is on our roadmap, though, to implement support for popular design driven API technologies.  This means in future it will be possible to generate Lagom service interfaces from specifications in these technologies, allowing transparent integration across any framework that supports these technologies.
