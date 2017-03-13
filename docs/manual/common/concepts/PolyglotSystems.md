# Polyglot systems with Lagom

 Lagom does not expect that every service in your system will be a Lagom microservice.  After all, a big advantage of using microservices in the first place is to pragmatically select the best language and technology for each service. Lagom's idiomatic usage of standard comunication protocols and general compatibility with design driven API approaches allow it to work well in such a polyglot system.

## Lagom communication protocols

Lagom service calls map down to standard HTTP for synchronous communication and WebSockets for streaming and asynchronous messaging. Any language or framework that supports these protocols can easily consume a Lagom service.  Conversely, a Lagom service can easily talk to any service that exposes a REST API.

A microservice's API specifies how that service uses HTTP. REST service calls are identified by an HTTP method and URI. Request and response headers can be customized. Lagom messages are serialised, by default, to ordinary JSON, using idiomatic mapping libraries that make it transparent how the JSON will be represented on the wire.

## Design driven APIs

Lagom's interface specifications are designed to be compatible with design driven API approaches to system development. However currently, aside from using the same interfaces in Lagom to Lagom service communication, Lagom does not include specific support for design driven API development. In future releases, we plan to implement support for popular design driven API technologies, which will make it  possible to generate Lagom service interfaces from specifications and allow transparent integration across any framework that supports these technologies.

**Reviewers:** Is it acceptable to document future plans? In commercial product doc, that was a no-no, but open source might be quite different.
