# Sizing individual microservices

Whether you're building a new system from scratch, or decomposing a monolith into microservices, answers to the following questions will help you make good choices.

### Does this service do only one thing?

It should. If you can't state a microservice's full purpose with a short sentence, your service may be too big. For example:

* An appropriately sized service: "This service manages friend relationships between users." 
* A service that performs multiple functions and should probably be split up: "This service manages friend relationships between users and aggregates all the activity for a user's friends so it can be consumed". 

### Is this service autonomous?

A service should be responsible for its own behavior. It shouldn't rely on other services to do its job.

For example, consider an order service whose protocol allows you to create an order, add items, add payment details, and confirm the order by paying. However, another service handles payment. What if the payment service isn't running? This dependency means the order service is not autonomous.

An autonomous service would accept the confirmation request regardless of the status of the payment service. It might also, probably asynchronously, ensure that the payment is eventually processed.

### Does this service own its own data?

A service "owns" data if it is the sole writer *and* the sole reader of the database where the data lives.

If you find yourself designing multiple services that access the same database, consider a redesign. Pick one service as owner; require the other services to use that service's protocol to make read requests.