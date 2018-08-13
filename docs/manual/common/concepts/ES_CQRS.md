# Managing data persistence

When designing your microservices, remember that each service should own its data and have direct access to the database. Other services should then use the Service API to interact with the data. There must be no sharing of databases across different services since that would result in a too-tight coupling between the services. In this way, each microservice operates within a clear boundary, similar to the [Bounded Context](https://martinfowler.com/bliki/BoundedContext.html) strategic pattern in Domain-driven Design.

To achieve this decoupled architecture, Lagom's persistence module promotes the use of [event sourcing](https://msdn.microsoft.com/en-us/library/jj591559.aspx) and [CQRS](https://msdn.microsoft.com/en-us/library/jj591573.aspx). Event sourcing is the practice of capturing all changes as domain events, which are immutable facts of things that have happened. For example, in a system using ES, when Mike withdraws money from his bank account, that event can be stored simply as "$100.00 withdrawn", rather than the complex interaction that would take place in a CRUD application, where a variety of reads and updates would need to take place before the wrapping transaction commits.

Event Sourcing is used for an  Aggregate Root, such as a customer with a given customer identifier --- Mike, in the previous example (see [What is an Aggregate](http://cqrs.nu/Faq/aggregates) for more details). The write-side is fully consistent within the aggregate. This makes it easy to reason about things like maintaining invariants and validating incoming commands. A difference you will need to note when adopting this model is that the aggregate can reply to queries for a specific identifier but it cannot be used for serving queries that span more than one aggregate. Therefore, you need to create another view of the data that is tailored to the queries that the service provides.

Lagom persists the event stream in the database. Event stream processors, other services or clients, read and optionally, act on, stored events. Lagom supports [[persistent read-side processors|ReadSide]] and [[message broker topic subscribers|MessageBrokerApi]]. You can also create your own event stream processor using the [[raw stream of events|ReadSide]].

If you do not want to use event sourcing and CQRS, you should probably use something other than the Persistence module in Lagom. (However, we suggest that you read [[Advantages of Event Sourcing|ESAdvantage]] first.) If you opt not to use Lagom's persistence module, the `CassandraSession` in the Lagom Persistence module provides an asynchronous API for storing data in Cassandra. But, you can implement your Lagom services with any data storage solution.

Should you choose to use something other than Lagom's persistence module, remember to use asynchronous APIs to achieve better scalability. If you are using blocking APIs, such JDBC or JPA, you should carefully manage the blocking by using dedicated thread pools of fixed/limited size for the components that are calling those blocking APIs. Never cascade the blocking through several asynchronous calls, such as Service API calls.



