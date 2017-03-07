# Managing data persistence

If a service stores information, a core principle is that each service should own its data and it is only the service itself that should have direct access to the database. Other services must use the Service API to interact with the data. There must be no sharing of databases across different services since that would result in a too tight coupling between the services. This is like a [Bounded Context](http://martinfowler.com/bliki/BoundedContext.html) in Domain-Driven Design terminology. Each service defines a Bounded Context.

To achieve this, Lagom's persistence module promotes use of [event sourcing](https://msdn.microsoft.com/en-us/library/jj591559.aspx) and [CQRS](https://msdn.microsoft.com/en-us/library/jj591573.aspx).

If you do not want to use event sourcing and CQRS, you should probably use something else than the Persistence module in Lagom. You can implement your Lagom services with any data storage solution. Use asynchronous APIs to achieve best scalability. If you are using blocking APIs, such JDBC or JPA, you should carefully manage the blocking by using dedicated thread pools of fixed/limited size for the components that are calling those blocking APIs. Never cascade the blocking through several asynchronous calls, such as Service API calls.

If you opt not to use Lagom's persistence module, the `CassandraSession` in the Lagom Persistence module provides an asynchronous API for storing data in Cassandra.

When using event sourcing, we capture all changes as domain events, which are immutable facts of things that have happened. For example "the seat was reserved by Alice". The events are stored and the current state can be derived from the events.

## Advantages of event sourcing

Here are some advantages of using event sourcing:

* There is no need for advanced mapping between domain objects and database representation since we only append events to a [log](https://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying). The events themselves are an important part of the domain model.

* The history of how we reached the current state is entirely in the stored events. Consistency between transactional data and audit data is assured, because these are actually the same data.

* We can analyze the event stream and derive important business information from it -- perhaps things that were not even thought about when the events were designed. We can add new views on our system's activity without making the write-side more complicated.

* We can get very good write performance, since we only append the events to the data store. There are no updates and no deletes.

* Event-sourced systems are easy to test and debug. Commands and events can be simulated for test purposes. We can also use the event log for debugging. If an issue is detected in production, we can replay the event log in a controlled environment to understand how we reached the bad state.

### Aggregate Root and CQRS

Event Sourcing is used for an [Aggregate Root](https://martinfowler.com/bliki/DDD_Aggregate.html). For example, a customer with a given customer identifier. The write-side is fully consistent within an aggregate. This makes it easy to reason about things like maintaining invariants and validating incoming commands.

The aggregate can reply to queries for a specific identifier but it cannot be used for serving queries that span more than one aggregate. Therefore you need to create another view of the data that is tailored to the queries that the service provides.


