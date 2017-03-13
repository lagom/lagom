# Managing data persistence

As mentioned previously, each service should own its data and have direct access to the database. Other services must use the Service API to interact with the data. There must be no sharing of databases across different services since that would result in a too tight coupling between the services. Similar to a [Bounded Context](http://martinfowler.com/bliki/BoundedContext.html) in Domain-Driven Design terminology, each microservice should define a Bounded Context.

To achieve this decoupled architecture, Lagom's persistence module promotes use of [event sourcing](https://msdn.microsoft.com/en-us/library/jj591559.aspx) and [CQRS](https://msdn.microsoft.com/en-us/library/jj591573.aspx). Event sourcing is the practice of capturing all changes as domain events, which are immutable facts of things that have happened. For example, when Alice reserves a seat, the system stores that event as "the seat was reserved by Alice". Once events are stored, the current state can be derived from the events. Past state can be reconstructed by replaying the event log.

Event Sourcing is used for an [Aggregate Root](http://martinfowler.com/bliki/DDD_Aggregate.html), such as a customer with a given customer identifier. The write-side is fully consistent within an aggregate. This makes it easy to reason about things like maintaining invariants and validating incoming commands. The aggregate can reply to queries for a specific identifier but it cannot be used for serving queries that span more than one aggregate. Therefore you need to create another view of the data that is tailored to the queries that the service provides.

**Reviewers:** I don't understand the previous paragraph or its implications? 

If you do not want to use event sourcing and CQRS, you should probably use something other than the Persistence module in Lagom. (However, we suggest that you read [[Advantages of Event Sourcing|ESAdvantage]] first.) If you opt not to use Lagom's persistence module, the `CassandraSession` in the Lagom Persistence module provides an asynchronous API for storing data in Cassandra. But, you can implement your Lagom services with any data storage solution. 

Should you choose to use something other than Lagom's persistence module, remember to use asynchronous APIs to achieve best scalability. If you are using blocking APIs, such JDBC or JPA, you should carefully manage the blocking by using dedicated thread pools of fixed/limited size for the components that are calling those blocking APIs. Never cascade the blocking through several asynchronous calls, such as Service API calls.





