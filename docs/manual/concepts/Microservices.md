# Microservices in Lagom

Bonér's [Reactive Microservices Architecture: Design Principles for Distributed Systems](http://www.oreilly.com/programming/free/reactive-microservices-architecture.html) is fundamental reading on architecting microservices.

We can't add too much more here. Becoming a good architect of microservice systems requires time and experience. However, Lagom does attempt to guide your decision-making by offering opinionated APIs, features, and defaults.

## Basic questions

Below are questions you should ask yourself as you design your services. The answers will guide you towards designing appropriately sized services with the right protocols.

The same questions apply, regardless of whether you're building a new system from scratch, or extracting microservices from an existing monolith.

### Does this service do only one thing?

It should. If you can't state a microservice's full purpose with a short sentence, your service may be too big.

Examples:

* Good: "This service manages friend relationships between users." This service is appropriately sized.
* Bad: "This service manages friend relationships between users and aggregates all the activity for a user's friends so it can be consumed". This service is doing more than one thing and should probably be split up.

### Is this service autonomous?

A service should be responsible for its own behavior. It shouldn't rely on other services to do its job.

For example, let's say you have an order service. Its protocol allows you to create an order, add items to it, add payment details to it, and confirm it. Confirming it requires invoking the payment service to make a payment. But what if the payment service isn't running? This dependency means the order service is not autonomous.

An autonomous service would accept the confirmation request regardless of the status of the payment service. It might also, probably asynchronously, ensure that the payment is eventually processed.

### Does this service own its own data?

A service "owns" data if it is the sole writer *and* the sole reader of the database where the data lives.

If you find yourself designing multiple services that access the same database, consider a redesign. Pick one service as owner; require the other services to use that service's protocol to make read requests.
