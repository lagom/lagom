# Advantages of Event Sourcing

For years, developers have implemented persistence using the traditional Create, Read, Update, Delete (CRUD) model. As previously introduced, the event sourcing model achieves persistence by storing state changes as historical events that capture business activity before writes occur to the data store. This decouples the events from the storage mechanism, allowing them to be aggregated, or placed in a group with logical boundaries. Event Sourcing is one of the patterns that enables concurrent, distributed systems to achieve high performance, scalability and resilience.

In a distributed architecture, event sourcing provides the following advantages:

* In a traditional CRUD model, entity instances are usually represented dually as a mutable object in memory and a mutable row in a relational database table. This leads to the infamous [object relational impedance mismatch](https://en.wikipedia.org/wiki/Object-relational_impedance_mismatch). Object-relational mappers were created to bridge this divide, but bring new complexities of their own. The event sourcing model treats the database as an append-only log of serialized events. It does not attempt to model the state of each entity or the relationships between them directly in the database schema. This greatly simplifies the code that writes to and reads from the database.

* The history of how an entity reached its current state remains in the stored events. Consistency between transactional data and audit data is assured, because these are actually the same data.

* You now have the ability to analyze the event stream and derive important business information from it -- perhaps things that were not even thought about when the events were designed. You can add new views on our system's activity without making the write-side more complicated.

* It improves write performance, since all types of events are simply appended to the data store. There are no updates and no deletes.

* Event-sourced systems are easy to test and debug. Commands and events can be simulated for test purposes. The event log provides a good record for debugging. If an issue is detected in production, you can replay the event log in a controlled environment to understand how an entity reached the bad state.

## Further reading
A full discussion of Event Sourcing and CQRS is beyond the scope of this documentation. For more information, we recommend the following.

* The [CQRS Journey](https://msdn.microsoft.com/en-us/library/jj554200.aspx) --- a great resource for learning more about CQRS and Event Sourcing.
* A [blog post by one of our own](https://jazzy.id.au/2016/10/08/cqrs-increases-consistency.html) --- shows the important role CQRS plays in a microservices system. 